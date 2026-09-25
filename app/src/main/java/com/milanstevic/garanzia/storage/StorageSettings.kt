package com.milanstevic.garanzia.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StorageTarget {
    PHONE,
    DRIVE,
}

data class StorageTargetState(
    val uri: Uri?,
    val label: String?,
)

data class StorageSettingsState(
    val phone: StorageTargetState = StorageTargetState(null, null),
    val drive: StorageTargetState = StorageTargetState(null, null),
) {
    val phoneConfigured: Boolean get() = phone.uri != null
    val driveConfigured: Boolean get() = drive.uri != null
    val bothConfigured: Boolean get() = phoneConfigured && driveConfigured
}

@Singleton
class StorageSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences("storage_targets", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<StorageSettingsState> = _state.asStateFlow()

    fun saveTarget(
        target: StorageTarget,
        uri: Uri,
        label: String?,
    ) {
        when (target) {
            StorageTarget.PHONE ->
                preferences.edit()
                    .putString(KEY_PHONE_URI, uri.toString())
                    .putString(KEY_PHONE_LABEL, label)
                    .apply()

            StorageTarget.DRIVE ->
                preferences.edit()
                    .putString(KEY_DRIVE_URI, uri.toString())
                    .putString(KEY_DRIVE_LABEL, label)
                    .apply()
        }

        _state.value = load()
    }

    fun clearTarget(target: StorageTarget) {
        val currentUri =
            when (target) {
                StorageTarget.PHONE -> _state.value.phone.uri
                StorageTarget.DRIVE -> _state.value.drive.uri
            }

        currentUri?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }

        when (target) {
            StorageTarget.PHONE ->
                preferences.edit()
                    .remove(KEY_PHONE_URI)
                    .remove(KEY_PHONE_LABEL)
                    .apply()

            StorageTarget.DRIVE ->
                preferences.edit()
                    .remove(KEY_DRIVE_URI)
                    .remove(KEY_DRIVE_LABEL)
                    .apply()
        }

        _state.value = load()
    }

    private fun load(): StorageSettingsState =
        StorageSettingsState(
            phone = StorageTargetState(
                uri = preferences.getString(KEY_PHONE_URI, null)?.let(Uri::parse),
                label = preferences.getString(KEY_PHONE_LABEL, null),
            ),
            drive = StorageTargetState(
                uri = preferences.getString(KEY_DRIVE_URI, null)?.let(Uri::parse),
                label = preferences.getString(KEY_DRIVE_LABEL, null),
            ),
        )

    private companion object {
        const val KEY_PHONE_URI = "phone_uri"
        const val KEY_PHONE_LABEL = "phone_label"
        const val KEY_DRIVE_URI = "drive_uri"
        const val KEY_DRIVE_LABEL = "drive_label"
    }
}
