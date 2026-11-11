package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val AddonHealthAlertDayMillis = 86_400_000L

/** An enabled add-on whose last health check could not reach it. */
internal data class UnresponsiveAddonAlert(
    val manifestUrl: String,
    val name: String,
)

/**
 * Picks the most recently checked enabled add-on that is down and has not
 * already been surfaced today. Returns null when nothing qualifies.
 */
internal fun selectUnresponsiveAddonAlert(
    results: Map<String, AddonCheckResult>,
    enabledManifestUrls: Set<String>,
    shownEpochDays: Map<String, Long>,
    nowEpochMs: Long,
): UnresponsiveAddonAlert? {
    val today = nowEpochMs.floorDiv(AddonHealthAlertDayMillis)
    return results.values
        .filter { result ->
            result.status == AddonHealthStatus.DOWN &&
                result.manifestUrl in enabledManifestUrls &&
                shownEpochDays[result.manifestUrl] != today
        }
        .maxByOrNull { result -> result.checkedAtEpochMs }
        ?.let { result ->
            UnresponsiveAddonAlert(
                manifestUrl = result.manifestUrl,
                name = result.name,
            )
        }
}

/** Remembers which add-on alerts were already shown today. */
internal object AddonHealthAlertRepository {
    private const val retentionDays = 30L

    private val _shownEpochDays = MutableStateFlow(AddonHealthCheckStorage.loadAlertShownEpochDays())
    val shownEpochDays: StateFlow<Map<String, Long>> = _shownEpochDays.asStateFlow()

    fun markShown(manifestUrl: String, nowEpochMs: Long) {
        val today = nowEpochMs.floorDiv(AddonHealthAlertDayMillis)
        if (_shownEpochDays.value[manifestUrl] == today) return

        val updated = _shownEpochDays.value
            .filterValues { epochDay -> epochDay >= today - retentionDays }
            .toMutableMap()
        updated[manifestUrl] = today
        _shownEpochDays.value = updated
        AddonHealthCheckStorage.saveAlertShownEpochDays(updated)
    }
}
