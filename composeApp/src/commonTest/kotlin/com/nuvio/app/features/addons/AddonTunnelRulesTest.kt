package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddonTunnelRulesTest {
    @Test
    fun `a connect line names the host and port the tunnel may reach`() {
        val target = parseAddonTunnelTarget("CONNECT addons.example.org:443 HTTP/1.1")

        assertEquals("addons.example.org", target?.host)
        assertEquals(443, target?.port)
        assertEquals("addons.example.org:443", target?.authority)
    }

    @Test
    fun `only a connect line is accepted`() {
        assertNull(parseAddonTunnelTarget("GET /manifest.json HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("POST http://addons.example.org/manifest.json HTTP/1.1"))
        assertNull(parseAddonTunnelTarget(""))
    }

    @Test
    fun `a malformed destination is refused`() {
        assertNull(parseAddonTunnelTarget("CONNECT addons.example.org HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT addons.example.org: HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT addons.example.org:0 HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT addons.example.org:70000 HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT user@addons.example.org:443 HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT single-label:443 HTTP/1.1"))
    }

    @Test
    fun `a literal private address is refused before the tunnel opens a socket`() {
        assertNull(parseAddonTunnelTarget("CONNECT 127.0.0.1:443 HTTP/1.1"))
        assertNull(parseAddonTunnelTarget("CONNECT 192.168.1.20:443 HTTP/1.1"))
        assertEquals(443, parseAddonTunnelTarget("CONNECT 93.184.216.34:443 HTTP/1.1")?.port)
    }

    @Test
    fun `the tunnel connects to the first public answer and refuses a mixed one`() {
        assertEquals("93.184.216.34", chooseAddonTunnelAddress(listOf("93.184.216.34")))
        assertEquals(
            "93.184.216.34",
            chooseAddonTunnelAddress(listOf("93.184.216.34", "2606:2800:220:1::1")),
        )
        assertNull(chooseAddonTunnelAddress(listOf("93.184.216.34", "10.1.2.3")))
        assertNull(chooseAddonTunnelAddress(listOf("127.0.0.1")))
        assertNull(chooseAddonTunnelAddress(emptyList()))
    }
}
