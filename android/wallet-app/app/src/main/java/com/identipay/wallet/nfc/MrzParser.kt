package com.identipay.wallet.nfc

import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses MRZ key fields and computes BAC keys for passport authentication.
 */
@Singleton
class MrzParser @Inject constructor() {

    /**
     * Create a BAC key from MRZ fields.
     * @param docNumber Document number (max 9 chars)
     * @param dateOfBirth Date of birth in YYMMDD format
     * @param expiryDate Expiry date in YYMMDD format
     */
    fun createBacKey(
        docNumber: String,
        dateOfBirth: String,
        expiryDate: String,
    ): BACKey {
        val cleanDocNumber = docNumber.trim().uppercase().padEnd(9, '<')
        val cleanDob = dateOfBirth.trim().replace("-", "").replace("/", "")
        val cleanExpiry = expiryDate.trim().replace("-", "").replace("/", "")

        validateDateFormat(cleanDob, "date of birth")
        validateDateFormat(cleanExpiry, "expiry date")

        return BACKey(cleanDocNumber, cleanDob, cleanExpiry)
    }

    /**
     * Validate that a date string is in YYMMDD format.
     */
    private fun validateDateFormat(date: String, fieldName: String) {
        require(date.length == 6) {
            "Invalid $fieldName format: must be YYMMDD (got '$date')"
        }
        require(date.all { it.isDigit() }) {
            "Invalid $fieldName format: must contain only digits (got '$date')"
        }
        val month = date.substring(2, 4).toInt()
        val day = date.substring(4, 6).toInt()
        require(month in 1..12) {
            "Invalid $fieldName: month must be 01-12 (got $month)"
        }
        require(day in 1..31) {
            "Invalid $fieldName: day must be 01-31 (got $day)"
        }
    }

    /**
     * Validate document number format.
     */
    fun isValidDocNumber(docNumber: String): Boolean {
        val clean = docNumber.trim().uppercase()
        return clean.isNotEmpty() && clean.length <= 9 && clean.all { it.isLetterOrDigit() || it == '<' }
    }

    /**
     * Validate date format (YYMMDD).
     */
    fun isValidDate(date: String): Boolean {
        val clean = date.trim().replace("-", "").replace("/", "")
        return try {
            validateDateFormat(clean, "date")
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
