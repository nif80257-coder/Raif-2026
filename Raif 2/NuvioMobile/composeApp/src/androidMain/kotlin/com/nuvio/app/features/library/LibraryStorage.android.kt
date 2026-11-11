package com.nuvio.app.features.library

import android.content.Context
import android.content.SharedPreferences

actual object LibraryStorage {
    private const val preferencesName = "nuvio_library"
    private fun payloadKey(profileId: Int) = "library_payload_$profileId"

    private var preferences: SharedPreferences? = null
    private var accountWatchlistOutboxPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        accountWatchlistOutboxPreferences = context.getSharedPreferences(
            "nuvio_account_watchlist_outbox",
            Context.MODE_PRIVATE,
        )
    }

    actual fun loadPayload(profileId: Int): String? =
        preferences?.getString(payloadKey(profileId), null)

    actual fun savePayload(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString(payloadKey(profileId), payload)
            ?.apply()
    }

    actual fun loadPendingAccountWatchlistPayload(accountId: String): String? =
        accountWatchlistOutboxPreferences?.getString(pendingAccountWatchlistKey(accountId), null)

    actual fun savePendingAccountWatchlistPayload(accountId: String, payload: String) {
        val outboxPreferences = checkNotNull(accountWatchlistOutboxPreferences) {
            "Account watchlist storage has not been initialized."
        }
        check(outboxPreferences.edit().putString(pendingAccountWatchlistKey(accountId), payload).commit()) {
            "Could not persist the pending account watchlist changes."
        }
    }

    actual fun clearPendingAccountWatchlistPayload(accountId: String) {
        val outboxPreferences = checkNotNull(accountWatchlistOutboxPreferences) {
            "Account watchlist storage has not been initialized."
        }
        check(outboxPreferences.edit().remove(pendingAccountWatchlistKey(accountId)).commit()) {
            "Could not clear the completed account watchlist changes."
        }
    }

    private fun pendingAccountWatchlistKey(accountId: String): String =
        "pending_account_watchlists_$accountId"
}
