package com.identipay.identipaypos.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import recieptservice.com.recieptservice.PrinterInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReceiptData(
    val merchantName: String,
    val merchantTagline: String,
    val items: List<ReceiptLineItem>,
    val subtotal: Double,
    val currency: String,
    val transactionId: String,
    val intentHash: String,
    val suiTxDigest: String,
    val ageVerification: Int?,
    val timestamp: Date = Date(),
)

data class ReceiptLineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val total: Double,
)

interface PrinterServiceAccessor {
    fun getPrinterService(): PrinterInterface?
    fun isBound(): Boolean
    fun attemptBind()
}

object ReceiptPrinter {

    private const val TAG = "ReceiptPrinter"
    private const val LINE = "------------------------"

    suspend fun print(accessor: PrinterServiceAccessor, data: ReceiptData) {
        val printer = accessor.getPrinterService()
        if (printer == null) {
            Log.e(TAG, "Printer service not bound")
            accessor.attemptBind()
            return
        }

        withContext(Dispatchers.IO) {
            try {
                printReceipt(printer, data)
                Log.i(TAG, "Receipt printed for tx ${data.transactionId.take(8)}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to print receipt", e)
            }
        }
    }

    private fun printReceipt(p: PrinterInterface, d: ReceiptData) {
        // ── Header ──
        p.setAlignment(1)
        p.setTextBold(true)
        p.setTextSize(28f)
        p.printText(d.merchantName.uppercase())
        p.nextLine(1)
        p.setTextBold(false)
        p.setTextSize(18f)
        p.printText(d.merchantTagline)
        p.nextLine(1)

        val dateStr = SimpleDateFormat("dd MMM yyyy  HH:mm:ss", Locale.US).format(d.timestamp)
        p.printText(dateStr)
        p.nextLine(1)

        p.setAlignment(0)
        p.setTextSize(18f)
        p.printText(LINE)
        p.nextLine(1)

        // ── Line items ──
        for (item in d.items) {
            // Name on its own line
            p.setTextSize(24f)
            p.setAlignment(0)
            p.printText(item.name)
            p.nextLine(1)
            // Qty and amount on next line, right-aligned
            p.setAlignment(2)
            p.setTextSize(24f)
            p.printText("x${item.quantity}   %.2f".format(item.total))
            p.nextLine(1)
        }

        p.setAlignment(0)
        p.setTextSize(18f)
        p.printText(LINE)
        p.nextLine(1)

        // ── Subtotal & fee ──
        p.setTextSize(24f)
        p.printTableText(
            arrayOf("Subtotal", "%.2f %s".format(d.subtotal, d.currency)),
            intArrayOf(2, 2),
            intArrayOf(0, 2),
        )
        p.nextLine(1)
        p.printTableText(
            arrayOf("Network fee", "FREE"),
            intArrayOf(2, 2),
            intArrayOf(0, 2),
        )
        p.nextLine(1)

        p.setTextSize(18f)
        p.printText(LINE)
        p.nextLine(1)

        // ── Total ──
        p.setTextBold(true)
        p.setTextSize(30f)
        p.printTableText(
            arrayOf("Total", "%.2f %s".format(d.subtotal, d.currency)),
            intArrayOf(2, 2),
            intArrayOf(0, 2),
        )
        p.nextLine(1)
        p.setTextBold(false)

        p.setTextSize(18f)
        p.printText(LINE)
        p.nextLine(2)

        // ── Transaction details ──
        // Print each as label on one line, value on the next
        p.setTextSize(18f)
        p.setAlignment(0)

        p.setTextBold(true)
        p.printText("Tx ID:")
        p.nextLine(1)
        p.setTextBold(false)
        p.printText(d.transactionId)
        p.nextLine(1)

        if (d.intentHash.isNotEmpty()) {
            p.setTextBold(true)
            p.printText("Intent:")
            p.nextLine(1)
            p.setTextBold(false)
            p.printText(d.intentHash)
            p.nextLine(1)
        }

        if (d.suiTxDigest.isNotEmpty()) {
            p.setTextBold(true)
            p.printText("Sui Tx:")
            p.nextLine(1)
            p.setTextBold(false)
            p.printText(d.suiTxDigest)
            p.nextLine(1)
        }

        p.nextLine(1)
        p.printText("Settlement: Atomic")
        p.nextLine(1)
        if (d.ageVerification != null && d.ageVerification > 0) {
            p.printText("Age: ${d.ageVerification}+ (ZK proof)")
            p.nextLine(1)
        }

        p.printText(LINE)
        p.nextLine(1)

        // ── Suiscan QR code ──
        if (d.suiTxDigest.isNotEmpty()) {
            p.setAlignment(1)
            p.setTextSize(18f)
            p.printText("Verify on Suiscan")
            p.nextLine(2)

            val url = "https://suiscan.xyz/testnet/tx/${d.suiTxDigest}"
            p.printQRCode(url, 5, 1)
            p.nextLine(2)

            p.printText(LINE)
            p.nextLine(1)
        }

        // ── Footer ──
        p.setAlignment(1)
        p.setTextSize(18f)
        p.printText("Powered by identiPay")
        p.nextLine(1)
        p.printText("identipay.me")
        p.nextLine(3)
    }
}
