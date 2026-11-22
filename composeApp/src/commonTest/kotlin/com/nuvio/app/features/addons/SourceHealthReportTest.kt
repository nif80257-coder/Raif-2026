package com.nuvio.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceHealthReportTest {

    private fun source(
        name: String,
        enabled: Boolean = true,
        paused: Boolean = false,
        streak: Int = 0,
        speedMs: Long? = null,
        results: Int? = null,
        neverPaused: Boolean = false,
        lastSuccessAtMs: Long? = null,
    ) = SourceHealthInput(
        manifestUrl = "https://$name.example/manifest.json",
        name = name,
        enabled = enabled,
        neverPaused = neverPaused,
        paused = paused,
        failureStreak = streak,
        speedMs = speedMs,
        resultCount = results,
        lastSuccessAtMs = lastSuccessAtMs,
    )

    @Test
    fun `a pause outranks the streak that caused it`() {
        val status = sourceHealthStatus(source(name = "dead", paused = true, streak = 3, speedMs = 15_000L))

        assertEquals(SourceHealthStatus.Paused, status)
        assertTrue(status.needsAttention)
    }

    @Test
    fun `a failing source outranks one that answered with nothing`() {
        val report = sourceHealthReport(
            listOf(
                source(name = "quiet", speedMs = 900L, results = 0),
                source(name = "broken", streak = 1, speedMs = 15_000L),
            ),
        )

        assertEquals(listOf("broken", "quiet"), report.rows.map { row -> row.name })
    }

    @Test
    fun `answering with no links is a state worth showing`() {
        assertEquals(
            SourceHealthStatus.Silent,
            sourceHealthStatus(source(name = "empty", speedMs = 400L, results = 0)),
        )
        assertEquals(
            SourceHealthStatus.Healthy,
            sourceHealthStatus(source(name = "useful", speedMs = 400L, results = 12)),
        )
    }

    @Test
    fun `the slow threshold starts at six seconds`() {
        assertEquals(
            SourceHealthStatus.Healthy,
            sourceHealthStatus(source(name = "ok", speedMs = SourceHealthSlowThresholdMs - 1L, results = 3)),
        )
        assertEquals(
            SourceHealthStatus.Slow,
            sourceHealthStatus(source(name = "slow", speedMs = SourceHealthSlowThresholdMs, results = 3)),
        )
    }

    @Test
    fun `a source that was never measured is untested rather than healthy`() {
        assertEquals(SourceHealthStatus.Untested, sourceHealthStatus(source(name = "fresh")))

        val report = sourceHealthReport(listOf(source(name = "fresh")))
        assertEquals(1, report.summary.untested)
        assertEquals(0, report.summary.healthy)
        assertEquals(0, report.summary.answering)
    }

    @Test
    fun `a disabled source is listed last and counted apart`() {
        val report = sourceHealthReport(
            listOf(
                source(name = "off", enabled = false, streak = 2),
                source(name = "healthy", speedMs = 800L, results = 5),
            ),
        )

        assertEquals(listOf("healthy", "off"), report.rows.map { row -> row.name })
        assertEquals(1, report.summary.disabled)
        assertEquals(1, report.summary.enabled)
        assertEquals(2, report.summary.total)
        assertFalse(report.rows.last().needsAttention)
    }

    @Test
    fun `sources in the same state put the slower one first`() {
        val report = sourceHealthReport(
            listOf(
                source(name = "quick", speedMs = 1_000L, results = 4),
                source(name = "sluggish", speedMs = 5_000L, results = 4),
            ),
        )

        assertEquals(listOf("sluggish", "quick"), report.rows.map { row -> row.name })
    }

    @Test
    fun `the summary counts every enabled source once`() {
        val report = sourceHealthReport(
            listOf(
                source(name = "a", speedMs = 900L, results = 8),
                source(name = "b", speedMs = 12_000L, results = 2),
                source(name = "c", speedMs = 500L, results = 0),
                source(name = "d", streak = 2, speedMs = 15_000L),
                source(name = "e", paused = true, streak = 3),
                source(name = "f"),
                source(name = "g", enabled = false),
            ),
        )

        val summary = report.summary
        assertEquals(7, summary.total)
        assertEquals(6, summary.enabled)
        assertEquals(1, summary.disabled)
        assertEquals(1, summary.healthy)
        assertEquals(1, summary.slow)
        assertEquals(1, summary.silent)
        assertEquals(1, summary.failing)
        assertEquals(1, summary.paused)
        assertEquals(1, summary.untested)
        assertEquals(3, summary.answering)
        assertEquals(4, summary.needsAttention)
        assertEquals(4, report.attention.size)
    }

    @Test
    fun `a never paused source still reports its streak`() {
        val row = sourceHealthReport(
            listOf(source(name = "stubborn", streak = 4, neverPaused = true, speedMs = 15_000L)),
        ).rows.single()

        assertEquals(SourceHealthStatus.Failing, row.status)
        assertTrue(row.neverPaused)
        assertEquals(4, row.failureStreak)
    }

    @Test
    fun `the last answer time is carried through for a source that went quiet`() {
        val row = sourceHealthReport(
            listOf(
                source(
                    name = "quiet",
                    speedMs = 900L,
                    results = 0,
                    lastSuccessAtMs = 1_700_000_000_000L,
                ),
            ),
        ).rows.single()

        assertEquals(SourceHealthStatus.Silent, row.status)
        assertEquals(1_700_000_000_000L, row.lastSuccessAtMs)
    }

    @Test
    fun `a nameless source falls back to its host`() {
        val report = sourceHealthReport(
            listOf(
                SourceHealthInput(
                    manifestUrl = "https://some-host.example/manifest.json",
                    name = "  ",
                    enabled = true,
                    speedMs = 700L,
                    resultCount = 3,
                ),
            ),
        )

        assertEquals("some-host.example", report.rows.single().name)
    }
}
