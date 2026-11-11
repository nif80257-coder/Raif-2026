package com.nuvio.app.features.settings

internal expect object AddonHealthCheckStorage {
    fun loadFingerprints(): Map<String, String>
    fun saveFingerprints(fingerprints: Map<String, String>)
    fun loadAlertShownEpochDays(): Map<String, Long>
    fun saveAlertShownEpochDays(days: Map<String, Long>)
}
