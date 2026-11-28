package com.nuvio.app.features.addons

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonHostResolutionTest {
    @Test
    fun `only public numeric addresses pass the resolver policy`() {
        assertTrue(isPublicNumericAddress("93.184.216.34"))
        assertTrue(isPublicNumericAddress("2606:2800:220:1:248:1893:25c8:1946"))
        assertTrue(isPublicNumericAddress("2606:2800:220:1::1"))
        // A carrier that only speaks IPv6 hands back IPv4 hosts inside the well-known NAT64 prefix.
        assertTrue(isPublicNumericAddress("64:ff9b::5db8:d822"))
        assertFalse(isPublicNumericAddress("64:ff9b::c0a8:101"))
        assertFalse(isPublicNumericAddress("64:ff9b::7f00:1"))

        assertFalse(isPublicNumericAddress("127.0.0.1"))
        assertFalse(isPublicNumericAddress("10.4.5.6"))
        assertFalse(isPublicNumericAddress("172.16.9.9"))
        assertFalse(isPublicNumericAddress("192.168.1.20"))
        assertFalse(isPublicNumericAddress("169.254.10.10"))
        assertFalse(isPublicNumericAddress("100.64.0.7"))
        assertFalse(isPublicNumericAddress("0.0.0.0"))

        assertFalse(isPublicNumericAddress("::1"))
        assertFalse(isPublicNumericAddress("fe80::1"))
        assertFalse(isPublicNumericAddress("fd00::1"))
        assertFalse(isPublicNumericAddress("::ffff:127.0.0.1"))

        assertFalse(isPublicNumericAddress(""))
        assertFalse(isPublicNumericAddress("example.org"))
        assertFalse(isPublicNumericAddress("1.2.3"))
        assertFalse(isPublicNumericAddress("01.2.3.4"))
    }

    @Test
    fun `a name that answers with a private address is refused before the request`() = runTest {
        assertFailsWith<AddonUnsafeDestinationException> {
            verifyAddonDestinationIsPublic("https://rebind.example.org/manifest.json") {
                listOf("127.0.0.1")
            }
        }
    }

    @Test
    fun `a name is refused when any of its answers is private`() = runTest {
        assertFailsWith<AddonUnsafeDestinationException> {
            verifyAddonDestinationIsPublic("https://mixed.example.org/manifest.json") {
                listOf("93.184.216.34", "10.1.2.3")
            }
        }
    }

    @Test
    fun `a name that answers only with public addresses is allowed`() = runTest {
        verifyAddonDestinationIsPublic("https://addons.example.org/manifest.json") {
            listOf("93.184.216.34", "2606:2800:220:1::1")
        }
    }

    @Test
    fun `a failed lookup is refused instead of requested`() = runTest {
        assertFailsWith<AddonUnsafeDestinationException> {
            verifyAddonDestinationIsPublic("https://gone.example.org/manifest.json") { emptyList() }
        }
    }

    @Test
    fun `a literal address is judged by the URL policy without a lookup`() = runTest {
        var lookups = 0

        verifyAddonDestinationIsPublic("https://93.184.216.34/manifest.json") {
            lookups += 1
            listOf("93.184.216.34")
        }
        assertEquals(0, lookups)

        assertFailsWith<AddonUnsafeDestinationException> {
            verifyAddonDestinationIsPublic("http://127.0.0.1:11470/manifest.json") {
                lookups += 1
                emptyList()
            }
        }
        assertEquals(0, lookups)
    }

    @Test
    fun `a refused destination is never retried`() {
        assertFalse(isRetryableAddonFetchFailure(AddonUnsafeDestinationException()))
    }
}
