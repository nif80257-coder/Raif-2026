package com.nuvio.app.features.settings

internal expect object AddonHealthCheckStorage {
    fun loadFingerprints(): Map<String, String>
    fun saveFingerprints(fingerprints: Map<String, String>)
    fun loadAlertShownEpochDays(): Map<String, Long>
    fun saveAlertShownEpochDays(days: Map<String, Long>)

    /** Consecutive stream-fetch failures per add-on, encoded as "failures|lastFailureEpochMs". */
    fun loadStreamFailures(): Map<String, String>
    fun saveStreamFailures(failures: Map<String, String>)

    /** The last check of each add-on, keyed by manifest URL, so the screen survives a restart. */
    fun loadCheckResults(): Map<String, String>
    fun saveCheckResults(results: Map<String, String>)
}
