package com.milanstevic.garanzia.warranty

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.milanstevic.garanzia.MainActivity
import com.milanstevic.garanzia.data.ReceiptRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WarrantyNotificationWorkerEntryPoint {
    fun receiptRepository(): ReceiptRepository
}

class WarrantyNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!canPostNotifications()) {
            return@withContext Result.success()
        }

        val repository = EntryPointAccessors
            .fromApplication(
                applicationContext,
                WarrantyNotificationWorkerEntryPoint::class.java,
            )
            .receiptRepository()

        ensureChannel()

        return@withContext try {
            val today = LocalDate.now()

            repository.getAllReceipts().forEach { receipt ->
                receipt.products.forEach { product ->
                    if (!product.warrantyNotificationsEnabled) return@forEach

                    val snapshot = WarrantyEngine.calculate(
                        purchaseDateIso = receipt.receipt.purchaseDate,
                        warrantyMonths = product.warrantyMonths,
                        reminderDays = product.warrantyReminderDays,
                        today = today,
                    ) ?: return@forEach

                    val notificationKey = snapshot.notificationKey ?: return@forEach
                    if (product.warrantyLastNotificationKey == notificationKey) {
                        return@forEach
                    }

                    postNotification(
                        productId = product.id,
                        productName = product.name,
                        merchant = receipt.receipt.merchant,
                        snapshot = snapshot,
                    )

                    repository.markWarrantyNotification(
                        receiptId = receipt.receipt.id,
                        productId = product.id,
                        notificationKey = notificationKey,
                    )
                }
            }

            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(
        productId: Long,
        productName: String,
        merchant: String,
        snapshot: WarrantySnapshot,
    ) {
        val title =
            when (snapshot.state) {
                WarrantyState.EXPIRED -> "Garanzia scaduta"
                WarrantyState.EXPIRING_SOON ->
                    if (snapshot.daysRemaining == 0) {
                        "Garanzia in scadenza oggi"
                    } else {
                        "Garanzia in scadenza"
                    }
                WarrantyState.ACTIVE -> return
            }

        val text =
            when (snapshot.state) {
                WarrantyState.EXPIRED ->
                    "${productName} · ${merchant}: garanzia scaduta il ${formatDate(snapshot.expiryDate)}."
                WarrantyState.EXPIRING_SOON ->
                    if (snapshot.daysRemaining == 0) {
                        "${productName} · ${merchant}: la garanzia scade oggi."
                    } else {
                        "${productName} · ${merchant}: ${snapshot.daysRemaining} giorni alla scadenza."
                    }
                WarrantyState.ACTIVE -> return
            }

        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            productId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(productId.hashCode(), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Scadenze garanzie",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Avvisi quando una garanzia sta per scadere o è scaduta."
            },
        )
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun formatDate(date: LocalDate): String =
        "%02d/%02d/%04d".format(
            date.dayOfMonth,
            date.monthValue,
            date.year,
        )

    private companion object {
        const val CHANNEL_ID = "warranty_expiry"
    }
}
