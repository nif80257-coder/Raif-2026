package com.nuvio.app.features.library

internal expect object LibraryStorage {
    fun loadPayload(profileId: Int): String?
    fun savePayload(profileId: Int, payload: String)
    fun loadPendingAccountWatchlistPayload(accountId: String): String?
    fun savePendingAccountWatchlistPayload(accountId: String, payload: String)
    fun clearPendingAccountWatchlistPayload(accountId: String)
}
