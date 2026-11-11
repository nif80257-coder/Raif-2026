package com.nuvio.app.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSettingsSyncOutboxTest {
    @Test
    fun `local snapshot persists per account and survives process restart`() {
        val disk = mutableMapOf<Pair<String, Int>, String>()
        fun newOutbox() = ProfileSettingsSyncOutbox(
            loadPayload = { account, profile -> disk[account to profile] },
            savePayload = { account, profile, payload -> disk[account to profile] = payload },
            clearPayload = { account, profile -> disk.remove(account to profile) },
        )
        val pending = PendingProfileSettingsChange("account-a", 2, "local snapshot")
        newOutbox().record(pending)

        assertEquals(pending, newOutbox().pendingFor("account-a", 2))
        assertNull(newOutbox().pendingFor("account-b", 2))
        assertNull(newOutbox().pendingFor("account-a", 1))
    }

    @Test
    fun `a failed older upload cannot clear a newer local snapshot`() {
        val disk = mutableMapOf<Pair<String, Int>, String>()
        val outbox = ProfileSettingsSyncOutbox(
            loadPayload = { account, profile -> disk[account to profile] },
            savePayload = { account, profile, payload -> disk[account to profile] = payload },
            clearPayload = { account, profile -> disk.remove(account to profile) },
        )
        val old = PendingProfileSettingsChange("account-a", 1, "old")
        val new = old.copy(payload = "new")
        outbox.record(old)
        outbox.record(new)

        assertFalse(outbox.clearIfMatches(old))
        assertEquals(new, outbox.pendingFor("account-a", 1))
        assertTrue(outbox.clearIfMatches(new))
        assertNull(outbox.pendingFor("account-a", 1))
    }
}
