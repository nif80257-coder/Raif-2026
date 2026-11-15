package com.cinemax.app.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BadgePackTest {

    private val pack = """
        {"filters":[
          {"id":"res-4k","name":"4K","pattern":"(?i)\\b2160p\\b","imageURL":"https://x/4k.png","isEnabled":true},
          {"id":"res-1080","name":"1080p","pattern":"(?i)\\b1080p\\b","imageURL":"https://x/1080.png","isEnabled":true},
          {"id":"off","name":"Disabled","pattern":"(?i)\\bcam\\b","imageURL":"https://x/cam.png","isEnabled":false},
          {"id":"noimg","name":"No image","pattern":"(?i)\\bhdr\\b","isEnabled":true},
          {"id":"bad","name":"Bad regex","pattern":"(?i)([unclosed","imageURL":"https://x/bad.png","isEnabled":true}
        ]}
    """.trimIndent()

    @Test
    fun parsingKeepsEnabledRulesWithImages() {
        val filters = parseBadgePack(pack)
        assertEquals(3, filters.size)
        assertTrue(filters.all { it.imageURL != null && it.isEnabled })
    }

    @Test
    fun matchingIsCaseInsensitiveAndOrdered() {
        val filters = parseBadgePack(pack)
        val matched = matchingBadges(filters, "Film.2024.2160p.HDR-GROUP")
        assertEquals(listOf("res-4k", "bad"), matched.map { it.id }.filter { it != "noimg" })
    }

    @Test
    fun invalidRegexIsSkippedInsteadOfThrowing() {
        val filters = parseBadgePack(pack)
        val matched = matchingBadges(filters, "2160p")
        assertEquals(listOf("res-4k"), matched.map { it.id })
    }

    @Test
    fun limitCapsTheBadgesDrawn() {
        val many = (1..10).map {
            BadgeFilter(id = "b$it", name = "b$it", pattern = "(?i)film", imageURL = "https://x/$it.png")
        }
        assertEquals(6, matchingBadges(many, "film").size)
        assertEquals(2, matchingBadges(many, "film", limit = 2).size)
    }

    @Test
    fun malformedPackYieldsNoBadges() {
        assertEquals(emptyList(), parseBadgePack("not json"))
        assertEquals(emptyList(), matchingBadges(emptyList(), "2160p"))
        assertEquals(emptyList(), matchingBadges(parseBadgePack(pack), ""))
    }

    @Test
    fun offeredPacksAreUniqueAndHttps() {
        assertTrue(BADGE_PACKS.isNotEmpty())
        assertEquals(BADGE_PACKS.size, BADGE_PACKS.map { it.second }.distinct().size)
        assertTrue(BADGE_PACKS.all { it.second.startsWith("https://") })
    }
}
