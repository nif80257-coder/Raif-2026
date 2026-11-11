package com.nuvio.app.features.party

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyInviteLinkTest {
    @Test
    fun inviteLinkTargetsTheGuestPageWhenOneIsConfigured() {
        val link = partyInviteLink("abc234")
        if (PARTY_WEB_BASE_URL.isBlank()) {
            assertEquals("raif://party/ABC234", link)
        } else {
            assertTrue(link.startsWith("https://"), link)
            assertTrue(link.endsWith("?code=ABC234"), link)
        }
    }

    @Test
    fun inviteLinkDropsCharactersTheCodeAlphabetDoesNotAllow() {
        val link = partyInviteLink(" abc-234 ")
        assertTrue(link.endsWith("ABC234") || link == "raif://party/ABC234", link)
    }

    @Test
    fun aBareSiteAddressGetsTheCodeAtItsRoot() {
        assertEquals(
            "https://raif-party.pages.dev/?code=ABC234",
            inviteLinkFor("https://raif-party.pages.dev", "abc234"),
        )
    }

    @Test
    fun aDirectoryBaseGetsThePageFileName() {
        assertEquals(
            "https://example.com/party/index.html?code=ABC234",
            inviteLinkFor("https://example.com/party/", "abc234"),
        )
    }

    @Test
    fun aPageFileBaseGetsTheCodeAlone() {
        assertEquals(
            "https://example.com/party/index.html?code=ABC234",
            inviteLinkFor("https://example.com/party/index.html", "abc234"),
        )
    }

    @Test
    fun aBlankBaseStaysOnTheAppScheme() {
        assertEquals("raif://party/ABC234", inviteLinkFor("  ", "abc234"))
    }
}
