package com.nuvio.app.features.party

internal expect object PartyResumeStorage {
    fun load(userId: String): PartyResumeSuggestion?
    fun save(userId: String, suggestion: PartyResumeSuggestion?)
}
