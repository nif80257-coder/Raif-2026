package com.nuvio.app.features.library

import platform.Foundation.NSUserDefaults

actual object LibraryStorage {
    private fun payloadKey(profileId: Int) = "library_payload_$profileId"

    actual fun loadPayload(profileId: Int): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey(profileId))

    actual fun savePayload(profileId: Int, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey(profileId))
    }

    actual fun loadPendingAccountWatchlistPayload(accountId: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(pendingAccountWatchlistKey(accountId))

    actual fun savePendingAccountWatchlistPayload(accountId: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = pendingAccountWatchlistKey(accountId))
    }

    actual fun clearPendingAccountWatchlistPayload(accountId: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(pendingAccountWatchlistKey(accountId))
    }

    private fun pendingAccountWatchlistKey(accountId: String): String =
        "pending_account_watchlists_$accountId"
}
