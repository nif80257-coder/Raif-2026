package com.nuvio.app.features.party

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object PartyHistoryStorage {
    private const val prefix = "party_history_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(PartyHistoryEntry.serializer())

    actual fun load(userId: String): List<PartyHistoryEntry> {
        val payload = NSUserDefaults.standardUserDefaults
            .stringForKey(prefix + userId)
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, payload) }.getOrDefault(emptyList())
    }

    actual fun save(userId: String, entries: List<PartyHistoryEntry>) {
        NSUserDefaults.standardUserDefaults.setObject(
            json.encodeToString(serializer, entries),
            forKey = prefix + userId,
        )
    }
}
