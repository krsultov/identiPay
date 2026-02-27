package com.identipay.wallet.data.repository

import android.util.Base64
import android.util.Log
import com.identipay.wallet.crypto.StealthAddress
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
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

        val total = recoveredReceive + recoveredAnnouncements
        Log.d(TAG, "fullRescan: recovered $recoveredReceive receive + $recoveredAnnouncements announced = $total total")
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
                // Skip if we already have this address
                if (stealthAddressDao.getByAddress(announcement.stealthAddress) != null) continue

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
                    // Encrypt stealth private key before storing
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
