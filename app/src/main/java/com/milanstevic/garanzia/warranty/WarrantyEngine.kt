package com.milanstevic.garanzia.warranty

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WarrantyState {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRED,
}

data class WarrantySnapshot(
    val purchaseDate: LocalDate,
    val warrantyMonths: Int,
    val expiryDate: LocalDate,
    val reminderDays: Int,
    val daysRemaining: Int,
    val state: WarrantyState,
) {
    val notificationKey: String?
        get() =
            when (state) {
                WarrantyState.ACTIVE -> null
                WarrantyState.EXPIRING_SOON -> "expiring:${expiryDate}"
                WarrantyState.EXPIRED -> "expired:${expiryDate}"
            }
}

object WarrantyEngine {

    fun calculate(
        purchaseDateIso: String,
        warrantyMonths: Int?,
        reminderDays: Int = DEFAULT_REMINDER_DAYS,
        today: LocalDate = LocalDate.now(),
    ): WarrantySnapshot? {
        if (warrantyMonths == null) return null

        require(warrantyMonths in 1..MAX_WARRANTY_MONTHS) {
            "Durata garanzia non valida"
        }
        require(reminderDays in 1..MAX_REMINDER_DAYS) {
            "Preavviso garanzia non valido"
        }

        val purchaseDate = LocalDate.parse(purchaseDateIso)
        val expiryDate = purchaseDate.plusMonths(warrantyMonths.toLong())
        val daysRemaining = ChronoUnit.DAYS
            .between(today, expiryDate)
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            .toInt()

        val state =
            when {
                daysRemaining < 0 -> WarrantyState.EXPIRED
                daysRemaining <= reminderDays -> WarrantyState.EXPIRING_SOON
                else -> WarrantyState.ACTIVE
            }

        return WarrantySnapshot(
            purchaseDate = purchaseDate,
            warrantyMonths = warrantyMonths,
            expiryDate = expiryDate,
            reminderDays = reminderDays,
            daysRemaining = daysRemaining,
            state = state,
        )
    }

    const val DEFAULT_REMINDER_DAYS = 30
    const val MAX_WARRANTY_MONTHS = 120
    const val MAX_REMINDER_DAYS = 365
}
