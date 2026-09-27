package com.milanstevic.garanzia.warranty

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WarrantyNotificationScheduler {

    fun ensureScheduled(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<WarrantyNotificationWorker>(
                REPEAT_HOURS,
                TimeUnit.HOURS,
            ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WarrantyNotificationWorker>().build(),
        )
    }

    private const val REPEAT_HOURS = 24L
    private const val PERIODIC_WORK_NAME = "garanzia_periodic_warranty_check"
    private const val IMMEDIATE_WORK_NAME = "garanzia_immediate_warranty_check"
}
