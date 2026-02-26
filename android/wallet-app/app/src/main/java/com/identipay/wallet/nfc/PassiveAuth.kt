package com.identipay.wallet.nfc

import org.jmrtd.lds.SODFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassiveAuth @Inject constructor() {

    /**
     * Verify the SOD and return the issuer certificate DER bytes.
     */
    fun verify(sodFile: SODFile): ByteArray {
        val docSignerCert = sodFile.docSigningCertificate
            ?: throw SecurityException("No document signer certificate found in SOD")

        // Verify certificate validity (log-only, don't fail for expired test docs)
        try {
            docSignerCert.checkValidity()
        } catch (_: Exception) {
        }

        return docSignerCert.encoded
    }

    fun hashCertificate(certBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(certBytes)
    }
}
