package com.nuvio.app.features.party

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

internal actual object PartyResumeStorage {
    private const val preferencesName = "nuvio_party_resume"
    private const val prefix = "party_resume_"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = PartyResumeSuggestion.serializer()

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun load(userId: String): PartyResumeSuggestion? {
        val payload = preferences
            ?.getString(prefix + userId, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { json.decodeFromString(serializer, payload) }.getOrNull()
    }

    actual fun save(userId: String, suggestion: PartyResumeSuggestion?) {
        val editor = preferences?.edit() ?: return
        if (suggestion == null) {
            editor.remove(prefix + userId).apply()
            return
        }
        editor.putString(prefix + userId, json.encodeToString(serializer, suggestion)).apply()
    }
}
