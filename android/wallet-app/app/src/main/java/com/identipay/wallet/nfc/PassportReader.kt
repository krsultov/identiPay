package com.identipay.wallet.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.identipay.wallet.crypto.IdentityCommitment
import net.sf.scuba.smartcards.CardService
import org.jmrtd.BACKey
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.LDSFileUtil
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PassportReader"

/**
 * Reads NFC passport/ID card data using JMRTD for ICAO 9303 compliance.
 * Supports both PACE (modern cards) and BAC (older cards) authentication.
 */
@Singleton
class PassportReader @Inject constructor(
    private val identityCommitment: IdentityCommitment,
    private val passiveAuth: PassiveAuth,
) {

    interface ReadCallback {
        fun onProgress(step: String)
        fun onSuccess(credential: CredentialData)
        fun onError(error: String)
    }

    fun enableReaderMode(activity: Activity, bacKey: BACKey, callback: ReadCallback) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: run {
            callback.onError("NFC not available on this device")
            return
        }

        nfcAdapter.enableReaderMode(
            activity,
            { tag -> readPassport(tag, bacKey, callback) },
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    fun disableReaderMode(activity: Activity) {
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
    }

    @Suppress("DEPRECATION")
    private fun readPassport(tag: Tag, bacKey: BACKey, callback: ReadCallback) {
        try {
            callback.onProgress("Connecting...")

            val isoDep = IsoDep.get(tag) ?: run {
                callback.onError("Tag does not support IsoDep")
                return
            }
            isoDep.timeout = 15000

            val cardService = CardService.getInstance(isoDep)
            cardService.open()

            val passportService = PassportService(
                cardService,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.DEFAULT_MAX_BLOCKSIZE,
                false,
                false,
            )
            passportService.open()

            callback.onProgress("Authenticating...")

            // Determine authentication method by reading EF.CardAccess.
            // EF.CardAccess is readable before authentication on PACE-capable cards.
            val paceInfo = try {
                val cardAccessStream =
                    passportService.getInputStream(PassportService.EF_CARD_ACCESS)
                val cardAccessFile = CardAccessFile(cardAccessStream)
                cardAccessFile.securityInfos
                    .filterIsInstance<PACEInfo>()
                    .firstOrNull()
            } catch (e: Exception) {
                Log.d(TAG, "No EF.CardAccess (BAC-only card): ${e.message}")
                null
            }

            if (paceInfo != null) {
                // PACE path (modern passports and ID cards).
                // BouncyCastle must be at provider position 1 for brainpool curve
                // support — this is done in IdentiPayApp.onCreate().
                Log.d(TAG, "Using PACE: oid=${paceInfo.objectIdentifier}, paramId=${paceInfo.parameterId}")
                val paceKeySpec = PACEKeySpec.createMRZKey(bacKey)
                passportService.doPACE(
                    paceKeySpec,
                    paceInfo.objectIdentifier,
                    PACEInfo.toParameterSpec(paceInfo.parameterId),
                    paceInfo.parameterId,
                )
                // After PACE, select applet over the established secure channel
                passportService.sendSelectApplet(true)
            } else {
                // BAC path (older passports without PACE).
                // Must select applet before BAC.
                passportService.sendSelectApplet(false)
                passportService.doBAC(bacKey)
            }

            callback.onProgress("Reading passport data...")

            // Read DG1 (MRZ data)
            val dg1Stream = passportService.getInputStream(PassportService.EF_DG1)
            val dg1File = LDSFileUtil.getLDSFile(PassportService.EF_DG1, dg1Stream) as DG1File
            val mrzInfo = dg1File.mrzInfo

            callback.onProgress("Reading security data...")

            // Read SOD (Document Security Object)
            val sodStream = passportService.getInputStream(PassportService.EF_SOD)
            val sodFile = SODFile(sodStream)

            callback.onProgress("Verifying document authenticity...")

            // Passive authentication
            val issuerCertBytes = passiveAuth.verify(sodFile)

            // Extract credential fields
            val personalNumber = mrzInfo.personalNumber
            if (personalNumber.isNullOrBlank()) {
                callback.onError("Passport does not contain a personal number (EGN). This passport type is not supported.")
                return
            }
            val dateOfBirth = mrzInfo.dateOfBirth
            val nationality = mrzInfo.nationality
            val issuer = mrzInfo.issuingState

            // Hash fields to BN254 field elements
            val md = MessageDigest.getInstance("SHA-256")
            val issuerCertHash = java.math.BigInteger(1, md.digest(issuerCertBytes))
                .mod(com.identipay.wallet.crypto.PoseidonHash.FIELD_PRIME)
            md.reset()
            val personalNumberHash = identityCommitment.hashToField(personalNumber)
            val dobHash = identityCommitment.hashToField(dateOfBirth)

            val credential = CredentialData(
                issuerCertHash = issuerCertHash,
                personalNumberHash = personalNumberHash,
                dobHash = dobHash,
                rawPersonalNumber = personalNumber,
                rawDateOfBirth = dateOfBirth,
                rawNationality = nationality,
                rawIssuer = issuer,
                issuerCertBytes = issuerCertBytes,
            )

            callback.onSuccess(credential)

        } catch (e: Exception) {
            Log.e(TAG, "Passport reading failed", e)
            callback.onError("Passport reading failed: ${e.message}")
        }
    }
}
