package com.nuvio.app.core.storage

/**
 * Where a secret is kept at rest: the Keychain on iOS, and the platform's own protected store
 * elsewhere.
 *
 * [name] is stable and namespaced by the caller (`tmdb.apiKey.<profile>`), so two settings never
 * share an item. A read returns null when the item is absent *or* the store refused to answer; a
 * write of null removes it. A refusal must not crash the settings screen — an API key that cannot
 * be read is a key the user has to type again, not a crash on launch.
 */
internal expect object SecretStore {
    fun read(name: String): String?
    fun write(name: String, value: String?)
    fun writeChecked(name: String, value: String?): Boolean
}
