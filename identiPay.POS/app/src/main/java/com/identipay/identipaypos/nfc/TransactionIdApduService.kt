package com.identipay.identipaypos.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

object ActiveTransactionHolder {
    @Volatile
    var currentTransactionId: String? = null
}

class TransactionIdApduService : HostApduService() {

    companion object {
        private const val TAG = "TransactionIdApduService"

        // Status Bytes (ISO 7816-4)
        private val SW_NO_ERROR = byteArrayOf(0x90.toByte(), 0x00.toByte()) // Success
        private val SW_WRONG_LENGTH = byteArrayOf(0x67.toByte(), 0x00.toByte())
        private val SW_DATA_NOT_FOUND =
            byteArrayOf(0x6A.toByte(), 0x88.toByte()) // Referenced data not found
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00.toByte())
        private val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E.toByte(), 0x00.toByte())

        private val SELECT_APDU_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
        private val IDENTIPAY_POS_AID = hexStringToByteArray("F123456789FDEF")

        private fun hexStringToByteArray(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] =
                    ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        private const val INS_GET_TRANSACTION_ID = 0x01
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) {
            return SW_DATA_NOT_FOUND
        }
        Log.d(TAG, "Received APDU: ${commandApdu.toHexString()}")

        if (isSelectAidApdu(commandApdu)) {
            Log.i(TAG, "SELECT AID command received and matched.")
            return SW_NO_ERROR
        }

        if (commandApdu.size >= 4 && commandApdu[1] == INS_GET_TRANSACTION_ID.toByte()) {
            Log.d(TAG, "GET_TRANSACTION_ID command received.")
            val transactionId = ActiveTransactionHolder.currentTransactionId
            return if (transactionId != null) {
                val txIdBytes = transactionId.toByteArray(StandardCharsets.UTF_8)
                Log.i(TAG, "Sending Transaction ID: $transactionId")
                txIdBytes + SW_NO_ERROR
            } else {
                Log.w(TAG, "Transaction ID requested but none is active.")
                SW_DATA_NOT_FOUND
            }
        }

        Log.w(TAG, "Unsupported APDU Instruction or CLA.")
        return SW_INS_NOT_SUPPORTED
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "HCE Service Deactivated, reason: $reason")
    }

    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        return apdu.size >= SELECT_APDU_HEADER.size + IDENTIPAY_POS_AID.size &&
                areArraysEqualInRange(apdu, 0, SELECT_APDU_HEADER, 0, SELECT_APDU_HEADER.size) &&
                apdu[SELECT_APDU_HEADER.size] == IDENTIPAY_POS_AID.size.toByte() &&
                areArraysEqualInRange(
                    apdu,
                    SELECT_APDU_HEADER.size + 1,
                    IDENTIPAY_POS_AID,
                    0,
                    IDENTIPAY_POS_AID.size
                )
    }

    private fun areArraysEqualInRange(
        array1: ByteArray,
        start1: Int,
        array2: ByteArray,
        start2: Int,
        length: Int
    ): Boolean {
        if (start1 + length > array1.size || start2 + length > array2.size) {
            return false
        }
        for (i in 0 until length) {
            if (array1[start1 + i] != array2[start2 + i]) {
                return false
            }
        }
        return true
    }

    private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
}