package com.nuvio.app.features.addons

/**
 * How long a source may take before it is worth showing above the healthy ones.
 *
 * The add-ons screen already shows the average next to each source, but a number alone says
 * nothing about whether the source is a problem. Six seconds is where a stream request stops
 * feeling instant and starts holding one of the eight fetch slots for a noticeable time.
 */
internal const val SourceHealthSlowThresholdMs = 6_000L

/** What the last real stream request says about one source. */
internal enum class SourceHealthStatus {
    /** The user turned it off, so nothing is expected of it. */
    Disabled,

    /** Three failures in a row: it is skipped until the pause runs out. */
    Paused,

    /** It failed its last request but is still asked. */
    Failing,

    /** It answered, and the answer held no links. */
    Silent,

    /** It answered, but slower than [SourceHealthSlowThresholdMs]. */
    Slow,

    /** No stream request has measured it yet. */
    Untested,

    /** It answers quickly and with results. */
    Healthy,
    ;

    val needsAttention: Boolean
        get() = this == Paused || this == Failing || this == Silent || this == Slow
}

/**
 * Everything known about one installed source, gathered from the three memories that record it.
 *
 * The fields are the raw measurements rather than a verdict, so the status is decided in one place
 * ([sourceHealthReport]) and can be tested without a screen.
 */
internal data class SourceHealthInput(
    val manifestUrl: String,
    val name: String,
    val enabled: Boolean,
    val neverPaused: Boolean = false,
    val paused: Boolean = false,
    val failureStreak: Int = 0,
    val failureReason: AddonFailureReason? = null,
    val lastFailureAtMs: Long? = null,
    val lastSuccessAtMs: Long? = null,
    val speedMs: Long? = null,
    val resultCount: Int? = null,
)

/** One source as the health section shows it. */
internal data class SourceHealthRow(
    val manifestUrl: String,
    val name: String,
    val status: SourceHealthStatus,
    val speedMs: Long? = null,
    val resultCount: Int? = null,
    val failureStreak: Int = 0,
    val failureReason: AddonFailureReason? = null,
    val lastFailureAtMs: Long? = null,
    val lastSuccessAtMs: Long? = null,
    val neverPaused: Boolean = false,
) {
    /** True when this source is worth putting in front of the user. */
    val needsAttention: Boolean
        get() = status.needsAttention
}

/** The counts the section states in one line, so the user does not have to add them up. */
internal data class SourceHealthSummary(
    val total: Int = 0,
    val enabled: Int = 0,
    val disabled: Int = 0,
    val healthy: Int = 0,
    val slow: Int = 0,
    val silent: Int = 0,
    val failing: Int = 0,
    val paused: Int = 0,
    val untested: Int = 0,
) {
    /** Enabled sources that answered their last request, with or without results. */
    val answering: Int
        get() = healthy + slow + silent

    val needsAttention: Int
        get() = paused + failing + silent + slow
}

internal data class SourceHealthReport(
    val summary: SourceHealthSummary,
    val rows: List<SourceHealthRow>,
) {
    /** The sources worth showing first, in the same order as [rows]. */
    val attention: List<SourceHealthRow>
        get() = rows.filter { row -> row.needsAttention }
}

/**
 * Decides the state of one source from what was measured.
 *
 * A pause is stronger than a streak (the streak is why it was paused), a streak is stronger than a
 * quiet answer (a source that fails now may answer later), and an empty answer is stronger than a
 * slow one: a fast source that returns nothing is less useful than a slow one that returns links.
 * A source that was never measured stays [SourceHealthStatus.Untested] instead of being called
 * healthy, so the section never promises more than the app knows.
 */
internal fun sourceHealthStatus(input: SourceHealthInput): SourceHealthStatus = when {
    !input.enabled -> SourceHealthStatus.Disabled
    input.paused -> SourceHealthStatus.Paused
    input.failureStreak > 0 -> SourceHealthStatus.Failing
    input.resultCount == 0 -> SourceHealthStatus.Silent
    (input.speedMs ?: 0L) >= SourceHealthSlowThresholdMs -> SourceHealthStatus.Slow
    input.speedMs == null && input.resultCount == null -> SourceHealthStatus.Untested
    else -> SourceHealthStatus.Healthy
}

/** The order the section lists the sources in: the ones that cost the user the most come first. */
internal fun sourceHealthRank(status: SourceHealthStatus): Int = when (status) {
    SourceHealthStatus.Paused -> 0
    SourceHealthStatus.Failing -> 1
    SourceHealthStatus.Silent -> 2
    SourceHealthStatus.Slow -> 3
    SourceHealthStatus.Untested -> 4
    SourceHealthStatus.Healthy -> 5
    SourceHealthStatus.Disabled -> 6
}

/**
 * One report from the whole installed list, so the section reads each memory once.
 *
 * Sources are ordered by what they cost the user, then by how slow they were, then by name: a dead
 * source that is paused costs nothing, while a source that takes twelve seconds every time is the
 * one worth fixing. Unmeasured sources sort after measured ones because there is nothing to rank
 * them by yet, and disabled ones come last because nothing is expected of them.
 */
internal fun sourceHealthReport(inputs: List<SourceHealthInput>): SourceHealthReport {
    val rows = inputs.map { input ->
        SourceHealthRow(
            manifestUrl = input.manifestUrl,
            name = input.name.ifBlank { input.manifestUrl.repositoryHost() },
            status = sourceHealthStatus(input),
            speedMs = input.speedMs,
            resultCount = input.resultCount,
            failureStreak = input.failureStreak,
            failureReason = input.failureReason,
            lastFailureAtMs = input.lastFailureAtMs,
            lastSuccessAtMs = input.lastSuccessAtMs,
            neverPaused = input.neverPaused,
        )
    }.sortedWith(
        compareBy<SourceHealthRow> { row -> sourceHealthRank(row.status) }
            .thenByDescending { row -> row.speedMs ?: -1L }
            .thenBy { row -> row.name.lowercase() }
            .thenBy { row -> row.manifestUrl },
    )

    val summary = SourceHealthSummary(
        total = rows.size,
        enabled = rows.count { row -> row.status != SourceHealthStatus.Disabled },
        disabled = rows.count { row -> row.status == SourceHealthStatus.Disabled },
        healthy = rows.count { row -> row.status == SourceHealthStatus.Healthy },
        slow = rows.count { row -> row.status == SourceHealthStatus.Slow },
        silent = rows.count { row -> row.status == SourceHealthStatus.Silent },
        failing = rows.count { row -> row.status == SourceHealthStatus.Failing },
        paused = rows.count { row -> row.status == SourceHealthStatus.Paused },
        untested = rows.count { row -> row.status == SourceHealthStatus.Untested },
    )

    return SourceHealthReport(summary = summary, rows = rows)
}
