package com.identipay.wallet.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.identipay.wallet.data.repository.BalanceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BalanceRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val balanceRepository: BalanceRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "BalanceRefresh"
    }

    override suspend fun doWork(): Result {
        return try {
            balanceRepository.refreshAll()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Balance refresh failed", e)
            Result.retry()
        }
    }
}
