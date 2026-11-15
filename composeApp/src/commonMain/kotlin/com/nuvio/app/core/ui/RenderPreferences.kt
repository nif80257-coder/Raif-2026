package com.nuvio.app.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal expect object RenderPreferencesStorage {
    fun loadReducedEffects(): Boolean?
    fun saveReducedEffects(enabled: Boolean)
}

/**
 * Rendering preferences that trade polish for speed.
 *
 * Reduced effects turns off the two things that cost more per frame than everything else
 * on screen together: the animated card depth and the ash transition. Nothing is hidden
 * or restyled — the cards and the screens stay exactly where they are, they simply stop
 * moving — so the app looks the same and scrolls smoothly on an older device.
 */
object RenderPreferences {
    private val _reducedEffects = MutableStateFlow(false)
    val reducedEffects: StateFlow<Boolean> = _reducedEffects.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _reducedEffects.value = RenderPreferencesStorage.loadReducedEffects() ?: false
    }

    fun setReducedEffects(enabled: Boolean) {
        ensureLoaded()
        if (_reducedEffects.value == enabled) return
        _reducedEffects.value = enabled
        RenderPreferencesStorage.saveReducedEffects(enabled)
    }
}
