package com.nuvio.app.features.party

import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object PartyResumeStorage {
    private const val prefix = "party_resume_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = PartyResumeSuggestion.serializer()

    actual fun load(userId: String): PartyResumeSuggestion? {
        val defaults = NSUserDefaults.standardUserDefaults
        val payload = defaults.stringForKey(prefix + userId)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
    }

    actual fun save(userId: String, suggestion: PartyResumeSuggestion?) {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = prefix + userId
        if (suggestion == null) {
            defaults.removeObjectForKey(key)
            return
        }
        defaults.setObject(json.encodeToString(serializer, suggestion), forKey = key)
    }
}
