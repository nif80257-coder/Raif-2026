package com.nuvio.app.features.party

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

internal actual object PartyHistoryStorage {
    private const val preferencesName = "nuvio_party_history"
    private const val prefix = "party_history_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(PartyHistoryEntry.serializer())

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun load(userId: String): List<PartyHistoryEntry> {
        val payload = preferences
            ?.getString(prefix + userId, null)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyList())
    }

    actual fun save(userId: String, entries: List<PartyHistoryEntry>) {
        preferences
            ?.edit()
            ?.putString(prefix + userId, json.encodeToString(serializer, entries))
            ?.apply()
    }
}
