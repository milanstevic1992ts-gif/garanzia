package com.milanstevic.garanzia.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PendingMirrorDeletion(
    val target: StorageTarget,
    val directoryName: String,
)

@Singleton
class PendingMirrorDeletionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences =
        context.getSharedPreferences("pending_mirror_deletions", Context.MODE_PRIVATE)

    fun add(
        target: StorageTarget,
        directoryName: String,
    ) {
        val updated = readRaw().toMutableSet().apply {
            add(encode(target, directoryName))
        }
        preferences.edit().putStringSet(KEY_ITEMS, updated).apply()
    }

    fun remove(
        target: StorageTarget,
        directoryName: String,
    ) {
        val updated = readRaw().toMutableSet().apply {
            remove(encode(target, directoryName))
        }
        preferences.edit().putStringSet(KEY_ITEMS, updated).apply()
    }

    fun all(): List<PendingMirrorDeletion> =
        readRaw().mapNotNull(::decode)

    private fun readRaw(): Set<String> =
        preferences.getStringSet(KEY_ITEMS, emptySet())?.toSet().orEmpty()

    private fun encode(
        target: StorageTarget,
        directoryName: String,
    ): String = "${target.name}|$directoryName"

    private fun decode(value: String): PendingMirrorDeletion? {
        val separator = value.indexOf('|')
        if (separator <= 0 || separator == value.lastIndex) return null

        val target = runCatching {
            StorageTarget.valueOf(value.substring(0, separator))
        }.getOrNull() ?: return null

        return PendingMirrorDeletion(
            target = target,
            directoryName = value.substring(separator + 1),
        )
    }

    private companion object {
        const val KEY_ITEMS = "items"
    }
}
