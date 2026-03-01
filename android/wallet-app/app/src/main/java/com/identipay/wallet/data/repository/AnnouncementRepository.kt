package com.identipay.wallet.data.repository

import android.util.Base64
import android.util.Log
import com.identipay.wallet.crypto.StealthAddress
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.db.entity.TransactionEntity
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.SuiClientProvider
import com.identipay.wallet.network.toHexString
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnnouncementRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val stealthAddress: StealthAddress,
    private val walletKeys: WalletKeys,
    private val stealthAddressDao: StealthAddressDao,
    private val transactionDao: TransactionDao,
    private val balanceRepository: dagger.Lazy<BalanceRepository>,
    private val userPreferences: UserPreferences,
    private val paymentRepository: dagger.Lazy<PaymentRepository>,
) {
    private var lastCursor: String? = null

    companion object {
        private const val TAG = "AnnouncementRepo"
    }

    /**
     * Full rescan: resets the cursor and re-scans all announcements from the
     * beginning. Any stealth addresses that were deleted locally but still
     * have on-chain announcements will be re-derived and re-saved.
     *
     * Use this for user-initiated refreshes so that lost/deleted records are
     * automatically recovered.
     *
     * @return number of new addresses found
     */
    suspend fun fullRescan(): Int {
        Log.d(TAG, "fullRescan: resetting cursor and re-scanning all announcements")
        lastCursor = null

        // Re-derive any self-generated receive addresses that are missing from DB
        val recoveredReceive = try {
            paymentRepository.get().recoverReceiveAddresses()
        } catch (e: Exception) {
            Log.e(TAG, "fullRescan: receive address recovery failed", e)
            0
        }

        // Re-scan on-chain announcements (P2P sends, settlements)
        val recoveredAnnouncements = scanNew()

        // Recover commerce artifacts (ReceiptObjects) from on-chain
        val recoveredArtifacts = try {
            recoverCommerceArtifacts()
        } catch (e: Exception) {
            Log.e(TAG, "fullRescan: artifact recovery failed", e)
            0
        }

        val total = recoveredReceive + recoveredAnnouncements + recoveredArtifacts
        Log.d(TAG, "fullRescan: recovered $recoveredReceive receive + $recoveredAnnouncements announced + $recoveredArtifacts artifacts = $total total")
        return total
    }

    /**
     * Scan for new announcements addressed to us.
     * Fetches all pages since last cursor, filters by viewTag + full ECDH scan,
     * stores matched stealth addresses in Room.
     *
     * @return number of new addresses found
     */
    suspend fun scanNew(): Int {
        Log.d(TAG, "scanNew: starting, hasKeys=${walletKeys.hasKeys()}")
        if (!walletKeys.hasKeys()) {
            Log.w(TAG, "scanNew: no wallet keys — skipping")
            return 0
        }

        val viewKeyPair = walletKeys.getViewKeyPair()
        val spendKeyPair = walletKeys.getSpendKeyPair()
        Log.d(TAG, "scanNew: viewPub=${viewKeyPair.publicKey.toHex()}, spendPub=${spendKeyPair.publicKey.toHex()}")
        var found = 0

        var hasMore = true
        var pageNum = 0
        while (hasMore) {
            pageNum++
            Log.d(TAG, "scanNew: fetching page $pageNum, cursor=$lastCursor")
            val page = try {
                backendApi.getAnnouncements(
                    limit = 100,
                    cursor = lastCursor,
                )
            } catch (e: Exception) {
                Log.e(TAG, "scanNew: failed to fetch announcements", e)
                return found
            }

            Log.d(TAG, "scanNew: page $pageNum has ${page.announcements.size} announcements, hasMore=${page.hasMore}")

            for (announcement in page.announcements) {
                // Check if we already have this address AND a transaction for it
                val existingAddr = stealthAddressDao.getByAddress(announcement.stealthAddress)
                val existingTx = transactionDao.getByDigest(announcement.txDigest)

                // Fully known — skip
                if (existingAddr != null && existingTx != null) continue

                // If address is unknown, perform ECDH scan to check if it's ours
                val isOurs = if (existingAddr != null) {
                    true
                } else {
                    val ephPubBytes = hexToBytes(announcement.ephemeralPubkey)
                    val result = try {
                        stealthAddress.scan(
                            viewPrivateKey = viewKeyPair.privateKey,
                            spendPrivateKey = spendKeyPair.privateKey,
                            spendPubkey = spendKeyPair.publicKey,
                            ephemeralPubkey = ephPubBytes,
                            announcedViewTag = announcement.viewTag,
                            announcedStealthAddress = announcement.stealthAddress,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "scanNew: scan failed for ${announcement.stealthAddress}", e)
                        null
                    }

                    if (result != null) {
                        Log.d(TAG, "scanNew: MATCH found! stealthAddr=${result.stealthAddress}")
                        val privKeyEnc = Base64.encodeToString(
                            result.stealthPrivateKey,
                            Base64.NO_WRAP,
                        )
                        stealthAddressDao.insert(
                            StealthAddressEntity(
                                stealthAddress = result.stealthAddress,
                                stealthPrivKeyEnc = privKeyEnc,
                                stealthPubkey = result.stealthPubkey.toHexString(),
                                ephemeralPubkey = announcement.ephemeralPubkey,
                                viewTag = announcement.viewTag,
                                createdAt = parseTimestamp(announcement.timestamp),
                            )
                        )
                        found++
                        true
                    } else {
                        false
                    }
                }

                // Recover the transaction record if missing
                if (isOurs && existingTx == null) {
                    val txInfo = try {
                        balanceRepository.get().queryTransactionInfo(
                            announcement.txDigest,
                            announcement.stealthAddress,
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "scanNew: tx query failed for ${announcement.txDigest}", e)
                        null
                    }

                    if (txInfo != null && txInfo.amount > 0L) {
                        // For commerce transactions, look up merchant public key
                        var merchantPubKey: String? = null
                        var merchantName: String? = txInfo.merchantName
                        if (txInfo.type == "commerce" && txInfo.merchantAddress != null) {
                            try {
                                val lookup = backendApi.lookupMerchantByAddress(txInfo.merchantAddress)
                                merchantPubKey = lookup?.publicKey
                                if (lookup?.name != null) merchantName = lookup.name
                            } catch (e: Exception) {
                                Log.e(TAG, "scanNew: merchant lookup failed for ${txInfo.merchantAddress}", e)
                            }
                        }
                        transactionDao.insert(
                            TransactionEntity(
                                txDigest = txInfo.txDigest,
                                type = txInfo.type,
                                amount = txInfo.amount,
                                counterpartyName = merchantName,
                                stealthAddress = announcement.stealthAddress,
                                timestamp = txInfo.timestamp,
                                buyerStealthAddress = txInfo.buyerStealthAddress,
                                merchantPublicKey = merchantPubKey,
                            )
                        )
                        Log.d(TAG, "scanNew: recovered ${txInfo.type} tx ${txInfo.txDigest} amount=${txInfo.amount}")
                    }
                }
            }

            lastCursor = page.nextCursor
            hasMore = page.hasMore
        }

        Log.d(TAG, "scanNew: done, found $found new addresses")
        val totalInDb = stealthAddressDao.getAllOnce().size
        Log.d(TAG, "scanNew: total stealth addresses in DB = $totalInDb")
        return found
    }

    /**
     * Query on-chain ReceiptObjects owned by each stealth address.
     * For any receipt whose settlement transaction is not already recorded,
     * create a "commerce" TransactionEntity from the SettlementEvent data.
     *
     * This catches commerce transactions that don't have USDC balance on
     * the receipt address (USDC goes to the merchant, receipts go to buyer).
     */
    suspend fun recoverCommerceArtifacts(): Int {
        if (!walletKeys.hasKeys()) return 0

        val receiptType = "${SuiClientProvider.PACKAGE_ID}::receipt::ReceiptObject"
        val addresses = stealthAddressDao.getAllOnce()
        var recovered = 0

        for (addr in addresses) {
            val receiptTxDigests = try {
                balanceRepository.get().queryOwnedReceipts(addr.stealthAddress, receiptType)
            } catch (e: Exception) {
                Log.e(TAG, "recoverCommerceArtifacts: query failed for ${addr.stealthAddress}", e)
                continue
            }

            for (txDigest in receiptTxDigests) {
                // Skip if we already have this transaction
                if (transactionDao.getByDigest(txDigest) != null) continue

                val txInfo = try {
                    balanceRepository.get().queryTransactionInfo(txDigest, addr.stealthAddress)
                } catch (e: Exception) {
                    Log.e(TAG, "recoverCommerceArtifacts: tx query failed for $txDigest", e)
                    continue
                }

                if (txInfo != null && txInfo.amount > 0L) {
                    // Look up merchant public key for artifact decryption
                    val merchantLookup = txInfo.merchantAddress?.let { merchantAddr ->
                        try {
                            backendApi.lookupMerchantByAddress(merchantAddr)
                        } catch (e: Exception) {
                            Log.e(TAG, "recoverCommerceArtifacts: merchant lookup failed for $merchantAddr", e)
                            null
                        }
                    }
                    transactionDao.insert(
                        TransactionEntity(
                            txDigest = txInfo.txDigest,
                            type = txInfo.type,
                            amount = txInfo.amount,
                            counterpartyName = merchantLookup?.name ?: txInfo.merchantName,
                            stealthAddress = addr.stealthAddress,
                            timestamp = txInfo.timestamp,
                            buyerStealthAddress = txInfo.buyerStealthAddress,
                            merchantPublicKey = merchantLookup?.publicKey,
                        )
                    )
                    recovered++
                    Log.d(TAG, "recoverCommerceArtifacts: recovered ${txInfo.type} tx $txDigest amount=${txInfo.amount}")
                }
            }
        }

        Log.d(TAG, "recoverCommerceArtifacts: recovered $recovered transactions")
        return recovered
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun parseTimestamp(iso: String): Long = try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
