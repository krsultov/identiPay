package com.identipay.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.identipay.wallet.worker.AnnouncementScanWorker
import com.identipay.wallet.worker.BalanceRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class IdentiPayApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Replace Android's stripped-down BouncyCastle with the full version.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        scheduleWorkers()
    }

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Announcement scanning every 15 minutes
        val announcementWork = PeriodicWorkRequestBuilder<AnnouncementScanWorker>(
            15, TimeUnit.MINUTES,
        ).addTag(AnnouncementScanWorker.TAG).build()

        workManager.enqueueUniquePeriodicWork(
            AnnouncementScanWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            announcementWork,
        )

        // Balance refresh every 15 minutes
        val balanceWork = PeriodicWorkRequestBuilder<BalanceRefreshWorker>(
            15, TimeUnit.MINUTES,
        ).addTag(BalanceRefreshWorker.TAG).build()

        workManager.enqueueUniquePeriodicWork(
            BalanceRefreshWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            balanceWork,
        )
    }
}
