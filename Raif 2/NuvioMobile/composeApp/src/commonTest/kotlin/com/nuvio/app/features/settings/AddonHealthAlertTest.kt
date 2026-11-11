package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddonHealthAlertTest {

    private val nowEpochMs = 10L * AddonHealthAlertDayMillis

    @Test
    fun picksTheMostRecentlyCheckedDownAddon() {
        val alert = selectUnresponsiveAddonAlert(
            results = mapOf(
                "https://a/manifest.json" to result(
                    url = "https://a/manifest.json",
                    name = "Add-on A",
                    status = AddonHealthStatus.DOWN,
                    checkedAtEpochMs = nowEpochMs - 5_000L,
                ),
                "https://b/manifest.json" to result(
                    url = "https://b/manifest.json",
                    name = "Add-on B",
                    status = AddonHealthStatus.DOWN,
                    checkedAtEpochMs = nowEpochMs - 1_000L,
                ),
            ),
            enabledManifestUrls = setOf("https://a/manifest.json", "https://b/manifest.json"),
            shownEpochDays = emptyMap(),
            nowEpochMs = nowEpochMs,
        )

        assertEquals("https://b/manifest.json", alert?.manifestUrl)
        assertEquals("Add-on B", alert?.name)
    }

    @Test
    fun ignoresAlertsAlreadyShownToday() {
        val alert = selectUnresponsiveAddonAlert(
            results = mapOf(
                "https://a/manifest.json" to result(
                    url = "https://a/manifest.json",
                    status = AddonHealthStatus.DOWN,
                    checkedAtEpochMs = nowEpochMs,
                ),
            ),
            enabledManifestUrls = setOf("https://a/manifest.json"),
            shownEpochDays = mapOf("https://a/manifest.json" to 10L),
            nowEpochMs = nowEpochMs,
        )

        assertNull(alert)
    }

    @Test
    fun showsAgainOnTheNextDay() {
        val alert = selectUnresponsiveAddonAlert(
            results = mapOf(
                "https://a/manifest.json" to result(
                    url = "https://a/manifest.json",
                    status = AddonHealthStatus.DOWN,
                    checkedAtEpochMs = nowEpochMs,
                ),
            ),
            enabledManifestUrls = setOf("https://a/manifest.json"),
            shownEpochDays = mapOf("https://a/manifest.json" to 9L),
            nowEpochMs = nowEpochMs,
        )

        assertEquals("https://a/manifest.json", alert?.manifestUrl)
    }

    @Test
    fun ignoresHealthyAddons() {
        val alert = selectUnresponsiveAddonAlert(
            results = mapOf(
                "https://a/manifest.json" to result(
                    url = "https://a/manifest.json",
                    status = AddonHealthStatus.OK,
                    checkedAtEpochMs = nowEpochMs,
                ),
                "https://b/manifest.json" to result(
                    url = "https://b/manifest.json",
                    status = AddonHealthStatus.SLOW,
                    checkedAtEpochMs = nowEpochMs,
                ),
            ),
            enabledManifestUrls = setOf("https://a/manifest.json", "https://b/manifest.json"),
            shownEpochDays = emptyMap(),
            nowEpochMs = nowEpochMs,
        )

        assertNull(alert)
    }

    @Test
    fun ignoresAddonsThatAreNoLongerEnabled() {
        val alert = selectUnresponsiveAddonAlert(
            results = mapOf(
                "https://a/manifest.json" to result(
                    url = "https://a/manifest.json",
                    status = AddonHealthStatus.DOWN,
                    checkedAtEpochMs = nowEpochMs,
                ),
            ),
            enabledManifestUrls = emptySet(),
            shownEpochDays = emptyMap(),
            nowEpochMs = nowEpochMs,
        )

        assertNull(alert)
    }

    private fun result(
        url: String,
        status: AddonHealthStatus,
        checkedAtEpochMs: Long,
        name: String = url,
        enabled: Boolean = true,
    ): AddonCheckResult = AddonCheckResult(
        manifestUrl = url,
        addonId = url,
        name = name,
        enabled = enabled,
        status = status,
        responseTimeMs = null,
        manifestParsed = status != AddonHealthStatus.DOWN,
        manifestError = null,
        manifestVersion = null,
        supportedTypes = emptySet(),
        catalogsCount = 0,
        knownTest = AddonKnownContentTest(videoId = AddonHealthCheckKnownVideoId),
        changedSinceLastCheck = null,
        checkedAtEpochMs = checkedAtEpochMs,
    )
}
