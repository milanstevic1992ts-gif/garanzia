package com.milanstevic.garanzia.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncStatus(
    val lastAttemptEpochMs: Long? = null,
    val lastSuccessEpochMs: Long? = null,
    val successfulCopies: Int = 0,
    val failedCopies: Int = 0,
    val lastMessage: String? = null,
    val running: Boolean = false,
)

@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences =
        context.getSharedPreferences("background_sync_status", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncStatus> = _state.asStateFlow()

    fun markRunning() {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putLong(KEY_LAST_ATTEMPT, now)
            .putBoolean(KEY_RUNNING, true)
            .apply()
        _state.value = load()
    }

    fun markSkipped(message: String) {
        val now = System.currentTimeMillis()
        preferences.edit()
            .putLong(KEY_LAST_ATTEMPT, now)
            .putInt(KEY_SUCCESSFUL_COPIES, 0)
            .putInt(KEY_FAILED_COPIES, 0)
            .putString(KEY_LAST_MESSAGE, message)
            .putBoolean(KEY_RUNNING, false)
            .apply()
        _state.value = load()
    }

    fun markResult(
        successfulCopies: Int,
        failedCopies: Int,
        message: String,
    ) {
        val now = System.currentTimeMillis()
        val editor = preferences.edit()
            .putLong(KEY_LAST_ATTEMPT, now)
            .putInt(KEY_SUCCESSFUL_COPIES, successfulCopies)
            .putInt(KEY_FAILED_COPIES, failedCopies)
            .putString(KEY_LAST_MESSAGE, message)
            .putBoolean(KEY_RUNNING, false)

        if (failedCopies == 0) {
            editor.putLong(KEY_LAST_SUCCESS, now)
        }

        editor.apply()
        _state.value = load()
    }

    private fun load(): SyncStatus =
        SyncStatus(
            lastAttemptEpochMs = preferences
                .getLong(KEY_LAST_ATTEMPT, 0L)
                .takeIf { it > 0L },
            lastSuccessEpochMs = preferences
                .getLong(KEY_LAST_SUCCESS, 0L)
                .takeIf { it > 0L },
            successfulCopies = preferences.getInt(KEY_SUCCESSFUL_COPIES, 0),
            failedCopies = preferences.getInt(KEY_FAILED_COPIES, 0),
            lastMessage = preferences.getString(KEY_LAST_MESSAGE, null),
            running = preferences.getBoolean(KEY_RUNNING, false),
        )

    private companion object {
        const val KEY_LAST_ATTEMPT = "last_attempt"
        const val KEY_LAST_SUCCESS = "last_success"
        const val KEY_SUCCESSFUL_COPIES = "successful_copies"
        const val KEY_FAILED_COPIES = "failed_copies"
        const val KEY_LAST_MESSAGE = "last_message"
        const val KEY_RUNNING = "running"
    }
}
