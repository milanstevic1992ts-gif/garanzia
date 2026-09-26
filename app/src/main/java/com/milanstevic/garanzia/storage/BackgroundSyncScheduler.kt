package com.milanstevic.garanzia.storage

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {

    fun ensureScheduled(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<ReceiptSyncWorker>(
                REPEAT_HOURS,
                TimeUnit.HOURS,
            )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<ReceiptSyncWorker>()
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_MINUTES,
                    TimeUnit.MINUTES,
                )
                .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private const val REPEAT_HOURS = 1L
    private const val MIN_BACKOFF_MINUTES = 10L
    private const val PERIODIC_WORK_NAME = "garanzia_periodic_receipt_sync"
    private const val IMMEDIATE_WORK_NAME = "garanzia_immediate_receipt_sync"
}
