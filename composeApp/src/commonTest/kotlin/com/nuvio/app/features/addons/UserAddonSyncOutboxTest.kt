package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject

class UserAddonSyncOutboxTest {
    @Test
    fun `pending edits survive restart and preserve unrelated remote rows`() {
        val disk = mutableMapOf<Pair<String, Int>, String>()
        fun newOutbox() = UserAddonSyncOutbox(
            loadPayload = { account, profile -> disk[account to profile] },
            savePayload = { account, profile, payload -> disk[account to profile] = payload },
            clearPayload = { account, profile -> disk.remove(account to profile) },
        )
        val change = PendingUserAddonChange(
            accountId = "account-a",
            profileId = 2,
            mutations = listOf(
                PendingUserAddonMutation(
                    addonId = "edited",
                    manifestUrl = "https://edited.example/manifest.json",
                    enabled = false,
                ),
                PendingUserAddonMutation(addonId = "removed"),
            ),
        )
        newOutbox().record(change)
        val afterRestart = newOutbox()
        assertEquals(change, afterRestart.pendingFor("account-a", 2))

        val merged = applyPendingUserAddonMutations(
            remoteRows = listOf(
                UserAddonSyncRecord("edited", "https://old.example/manifest.json"),
                UserAddonSyncRecord("removed", "https://removed.example/manifest.json"),
                UserAddonSyncRecord("untouched", "https://keep.example/manifest.json"),
            ),
            mutations = change.mutations,
        )
        assertEquals(listOf("edited", "untouched"), merged.map(UserAddonSyncRecord::addonId))
        assertEquals("https://edited.example/manifest.json", merged.first().manifestUrl)
        assertFalse(merged.first().enabled)
    }

    @Test
    fun `account and profile keys isolate pending edits and stale success cannot clear newer edits`() {
        val disk = mutableMapOf<Pair<String, Int>, String>()
        val outbox = UserAddonSyncOutbox(
            loadPayload = { account, profile -> disk[account to profile] },
            savePayload = { account, profile, payload -> disk[account to profile] = payload },
            clearPayload = { account, profile -> disk.remove(account to profile) },
        )
        val original = PendingUserAddonChange("account-a", 1, listOf(PendingUserAddonMutation("one")))
        val newer = original.copy(mutations = listOf(PendingUserAddonMutation("two")))
        val merged = original.copy(mutations = original.mutations + newer.mutations)
        outbox.record(original)
        outbox.record(newer)

        assertNull(outbox.pendingFor("account-b", 1))
        assertNull(outbox.pendingFor("account-a", 2))
        // A record carries the edit that just happened, so a second edit joins the queue instead of
        // wiping the first one that has not been pushed yet.
        assertEquals(merged, outbox.pendingFor("account-a", 1))
        assertFalse(outbox.clearIfMatches(original))
        assertEquals(merged, outbox.pendingFor("account-a", 1))
        assertTrue(outbox.clearIfMatches(merged))
    }
}
