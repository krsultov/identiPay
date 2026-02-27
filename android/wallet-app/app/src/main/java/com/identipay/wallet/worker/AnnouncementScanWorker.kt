package com.identipay.wallet.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.identipay.wallet.data.repository.AnnouncementRepository
import com.identipay.wallet.data.repository.BalanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AnnouncementScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val announcementRepository: AnnouncementRepository,
    private val balanceRepository: BalanceRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "AnnouncementScan"
    }

    override suspend fun doWork(): Result {
        return try {
            val found = announcementRepository.scanNew()
            if (found > 0) {
                Log.d(TAG, "Found $found new stealth addresses, refreshing balances")
                balanceRepository.refreshAll()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Announcement scan failed", e)
            Result.retry()
        }
    }
}
