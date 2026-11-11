package com.nuvio.app.features.party

internal expect object PartyHistoryStorage {
    fun load(userId: String): List<PartyHistoryEntry>
    fun save(userId: String, entries: List<PartyHistoryEntry>)
}
