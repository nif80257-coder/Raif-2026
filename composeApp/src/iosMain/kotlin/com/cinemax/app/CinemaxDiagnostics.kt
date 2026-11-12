package com.cinemax.app

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import platform.Foundation.NSUserDefaults

/**
 * Records an uncaught Kotlin exception in the app preferences. A crash that
 * happens before the interface is on screen otherwise leaves no trace, so the
 * next launch reads this and shows it to the viewer (and to us) instead of the
 * app just closing silently.
 */
object CinemaxDiagnostics {
    private const val KEY = "cinemax.last.kotlin.crash"
    private const val MAX_LENGTH = 12_000

    @OptIn(ExperimentalNativeApi::class)
    fun install() {
        setUnhandledExceptionHook { throwable ->
            persist(throwable)
        }
    }

    private fun persist(throwable: Throwable) {
        val text = buildString {
            appendLine(throwable.message ?: throwable.toString())
            appendLine(throwable::class.simpleName ?: "خطأ")
            appendLine(throwable.stackTraceToString())
        }
        NSUserDefaults.standardUserDefaults.setObject(text.take(MAX_LENGTH), forKey = KEY)
    }

    fun lastCrash(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY)?.takeIf { it.isNotBlank() }

    fun clear() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY)
    }
}
