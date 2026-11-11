package com.nuvio.app.features.library

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountWatchlistSyncOutboxTest {
    @Test
    fun `pending operations persist in order and remain isolated by account`() {
        val disk = mutableMapOf<String, String>()
        fun newOutbox() = AccountWatchlistSyncOutbox(
            loadPayload = { account -> disk[account] },
            savePayload = { account, payload -> disk[account] = payload },
            clearPayload = { account -> disk.remove(account) },
        )
        val create = mutation("account-a", "create", AccountWatchlistMutationKind.CREATE_LIST)
        val rename = mutation("account-a", "rename", AccountWatchlistMutationKind.RENAME_LIST)
        newOutbox().record(create)
        newOutbox().record(rename)

        val restarted = newOutbox()
        assertEquals(listOf(create, rename), restarted.pendingFor("account-a"))
        assertTrue(restarted.pendingFor("account-b").isEmpty())
    }

    @Test
    fun `retrying an unconfirmed create reuses its stable list identity`() {
        val disk = mutableMapOf<String, String>()
        val outbox = AccountWatchlistSyncOutbox(
            loadPayload = { account -> disk[account] },
            savePayload = { account, payload -> disk[account] = payload },
            clearPayload = { account -> disk.remove(account) },
        )
        val initial = mutation("account-a", "operation-one", AccountWatchlistMutationKind.CREATE_LIST)
        val retry = initial.copy(operationId = "operation-two")

        assertEquals(initial, outbox.recordOrReuseListCreate(initial))
        assertEquals(initial, outbox.recordOrReuseListCreate(retry))
        assertEquals(listOf(initial), outbox.pendingFor("account-a"))
    }

    @Test
    fun `clearing a completed operation preserves later queued operations`() {
        val disk = mutableMapOf<String, String>()
        val outbox = AccountWatchlistSyncOutbox(
            loadPayload = { account -> disk[account] },
            savePayload = { account, payload -> disk[account] = payload },
            clearPayload = { account -> disk.remove(account) },
        )
        val first = mutation("account-a", "first", AccountWatchlistMutationKind.CREATE_LIST)
        val second = mutation("account-a", "second", AccountWatchlistMutationKind.DELETE_LIST)
        outbox.record(first)
        outbox.record(second)

        assertFalse(outbox.clearIfMatches(first.copy(name = "not the stored operation")))
        assertTrue(outbox.clearIfMatches(first))
        assertEquals(listOf(second), outbox.pendingFor("account-a"))
        assertTrue(outbox.clearIfMatches(second))
        assertTrue(outbox.pendingFor("account-a").isEmpty())
    }

    private fun mutation(
        accountId: String,
        operationId: String,
        kind: AccountWatchlistMutationKind,
    ) = PendingAccountWatchlistMutation(
        operationId = operationId,
        accountId = accountId,
        kind = kind,
        watchlistId = "list-$operationId",
        name = "Later",
        metadata = JsonObject(emptyMap()),
    )
}
