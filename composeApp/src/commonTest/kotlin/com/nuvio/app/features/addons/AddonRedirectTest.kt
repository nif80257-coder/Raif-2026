package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddonRedirectTest {
    @Test
    fun `safe redirects resolve absolute root relative and path relative locations`() {
        val current = "https://addons.example.org/catalog/manifest.json"

        assertEquals(
            "https://addons.example.org/manifest.json",
            resolveSafeAddonRedirect(current, "/manifest.json"),
        )
        assertEquals(
            "https://addons.example.org/catalog/next.json",
            resolveSafeAddonRedirect(current, "next.json"),
        )
        assertEquals(
            "https://cdn.example.net/manifest.json",
            resolveSafeAddonRedirect(current, "https://cdn.example.net/manifest.json"),
        )
    }

    @Test
    fun `unsafe redirect schemes destinations and downgrade are rejected`() {
        val current = "https://addons.example.org/manifest.json"

        assertNull(resolveSafeAddonRedirect(current, "http://cdn.example.net/manifest.json"))
        assertNull(resolveSafeAddonRedirect(current, "ftp://cdn.example.net/file"))
        assertNull(resolveSafeAddonRedirect(current, "https://127.0.0.1/manifest.json"))
        assertNull(resolveSafeAddonRedirect(current, "https://user:pass@cdn.example.net/manifest.json"))
        assertNull(resolveSafeAddonRedirect(current, "javascript:alert(1)"))
        assertNull(resolveSafeAddonRedirect(current, "\\\\cdn.example.net\\manifest.json"))
    }

    @Test
    fun `redirect rejection is not retried`() {
        assertFalse(isRetryableAddonFetchFailure(AddonRedirectRejectedException()))
        assertTrue(isRetryableAddonFetchFailure(IllegalStateException("connection reset")))
    }
}
