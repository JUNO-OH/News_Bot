package com.junoh.morningbriefing

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class BriefingUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val json = BriefingRepository.fetchLatestJson(applicationContext)
            val data = BriefingRepository.parse(json)
            BriefingRepository.save(applicationContext, json)
            BriefingRepository.downloadNewsImages(applicationContext, data)
            MorningBriefingWidgetReceiver.updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            BriefingRepository.saveError(applicationContext, e.message ?: e.javaClass.simpleName)
            MorningBriefingWidgetReceiver.updateAll(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_REFRESH_NOW = "refresh_morning_briefing_now"
        private const val UNIQUE_PERIODIC_REFRESH = "periodic_morning_briefing_refresh"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BriefingUpdateWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_REFRESH_NOW,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BriefingUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_REFRESH,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
