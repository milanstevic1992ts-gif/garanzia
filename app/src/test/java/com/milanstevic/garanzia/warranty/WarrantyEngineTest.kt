package com.milanstevic.garanzia.warranty

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WarrantyEngineTest {

    @Test
    fun noDurationMeansNoRegisteredWarranty() {
        assertNull(
            WarrantyEngine.calculate(
                purchaseDateIso = "2026-01-10",
                warrantyMonths = null,
                today = LocalDate.of(2026, 2, 1),
            ),
        )
    }

    @Test
    fun calculatesActiveWarranty() {
        val snapshot = requireNotNull(
            WarrantyEngine.calculate(
                purchaseDateIso = "2026-01-10",
                warrantyMonths = 24,
                reminderDays = 30,
                today = LocalDate.of(2026, 9, 27),
            ),
        )

        assertEquals(LocalDate.of(2028, 1, 10), snapshot.expiryDate)
        assertEquals(WarrantyState.ACTIVE, snapshot.state)
    }

    @Test
    fun entersReminderWindow() {
        val snapshot = requireNotNull(
            WarrantyEngine.calculate(
                purchaseDateIso = "2025-10-20",
                warrantyMonths = 12,
                reminderDays = 30,
                today = LocalDate.of(2026, 9, 27),
            ),
        )

        assertEquals(23, snapshot.daysRemaining)
        assertEquals(WarrantyState.EXPIRING_SOON, snapshot.state)
        assertEquals("expiring:2026-10-20", snapshot.notificationKey)
    }

    @Test
    fun expiresAfterAnniversaryDate() {
        val snapshot = requireNotNull(
            WarrantyEngine.calculate(
                purchaseDateIso = "2025-09-20",
                warrantyMonths = 12,
                reminderDays = 30,
                today = LocalDate.of(2026, 9, 27),
            ),
        )

        assertEquals(-7, snapshot.daysRemaining)
        assertEquals(WarrantyState.EXPIRED, snapshot.state)
        assertEquals("expired:2026-09-20", snapshot.notificationKey)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDuration() {
        WarrantyEngine.calculate(
            purchaseDateIso = "2026-01-10",
            warrantyMonths = 0,
        )
    }
}
