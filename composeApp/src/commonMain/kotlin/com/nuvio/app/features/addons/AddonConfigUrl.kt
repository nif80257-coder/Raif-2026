package com.nuvio.app.features.addons

/**
 * The manifest link an add-on is installed with once the fields its manifest declares in
 * `config[]` are filled in: the same path, with the typed values as the query. Settings the
 * original link already carried are kept unless the form sets the same key, so a source that
 * already came configured keeps what the user did not touch.
 */
internal fun buildConfiguredManifestUrl(
    manifestUrl: String,
    values: List<Pair<String, String>>,
): String {
    val base = manifestUrl.substringBefore("?").trimEnd('/')
    val manifestPath = if (base.endsWith("/manifest.json")) base else "$base/manifest.json"
    val entered = values.filter { (_, value) -> value.isNotBlank() }
    if (entered.isEmpty()) return manifestPath

    val enteredKeys = entered.mapTo(mutableSetOf()) { (key, _) -> key }
    val retained = manifestUrl.substringAfter("?", "")
        .split("&")
        .filter { pair -> pair.isNotBlank() }
        .filterNot { pair -> pair.substringBefore("=") in enteredKeys }
    val query = (
        retained + entered.map { (key, value) ->
            "${key.encodeAddonPathSegment()}=${value.encodeAddonPathSegment()}"
        }
        ).joinToString("&")
    return if (query.isBlank()) manifestPath else "$manifestPath?$query"
}

/** True when every required field has a value, so the form can offer to install. */
internal fun List<AddonConfigField>.hasAllRequiredValues(values: Map<String, String>): Boolean =
    filter { field -> field.required }
        .all { field -> !values[field.key].isNullOrBlank() }
