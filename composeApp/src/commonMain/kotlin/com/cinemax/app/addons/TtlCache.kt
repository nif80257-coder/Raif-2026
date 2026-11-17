package com.cinemax.app.addons

import kotlinx.datetime.Clock

/**
 * A tiny in-memory cache with a time-to-live, so switching an episode, a tab or
 * reopening a title does not repeat a request that was answered a moment ago.
 * It is used from the main dispatcher only, so no locking is needed; the map is
 * bounded so a long session cannot grow without limit.
 */
class TtlCache<T>(
    private val ttlMs: Long,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) {
    private data class Entry<T>(val value: T, val expiresAt: Long)

    private val entries = mutableMapOf<String, Entry<T>>()

    fun get(key: String): T? {
        val entry = entries[key] ?: return null
        if (entry.expiresAt <= now()) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    fun put(key: String, value: T) {
        if (entries.size >= MAX_ENTRIES) entries.clear()
        entries[key] = Entry(value, now() + ttlMs)
    }

    fun clear() = entries.clear()

    private companion object {
        const val MAX_ENTRIES = 64
    }
}
