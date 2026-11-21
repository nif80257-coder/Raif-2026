package com.nuvio.app.features.addons

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.addons_manifest_missing_field
import org.jetbrains.compose.resources.getString

internal object AddonManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(
        manifestUrl: String,
        payload: String,
    ): AddonManifest {
        val root = json.parseToJsonElement(payload).jsonObject
        val defaultTypes = root.stringList("types")
        val defaultPrefixes = root.stringList("idPrefixes")

        return AddonManifest(
            id = root.requiredString("id"),
            name = root.requiredString("name"),
            description = root.optionalString("description").orEmpty(),
            version = root.requiredString("version"),
            logoUrl = root.optionalString("logo")?.resolveAgainstManifest(manifestUrl),
            backgroundUrl = root.optionalString("background")?.resolveAgainstManifest(manifestUrl),
            resources = root.resources(defaultTypes, defaultPrefixes),
            types = defaultTypes,
            idPrefixes = defaultPrefixes,
            catalogs = root.catalogs(),
            behaviorHints = root.behaviorHints(),
            transportUrl = manifestUrl,
            addonCatalogs = root.addonCatalogs(),
            configFields = root.configFields(),
        )
    }

    /**
     * The `addonCatalogs` array: `{type, id, name}` entries the source serves a directory for.
     *
     * A source repeats the same directory once per type it covers — Cinemeta lists "official" and
     * "community" eleven times between them — which read as eleven identical entries. The `all`
     * entry, when there is one, covers every type, so the repeats fold into one entry per `id`.
     */
    private fun JsonObject.addonCatalogs(): List<AddonCatalogRef> {
        val catalogs = array("addonCatalogs").mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val id = entry.optionalString("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            AddonCatalogRef(
                type = entry.optionalString("type")?.takeIf(String::isNotBlank) ?: "all",
                id = id,
                name = entry.optionalString("name")?.takeIf(String::isNotBlank) ?: id,
            )
        }
        val allTypes = catalogs.filter { catalog -> catalog.type.equals("all", ignoreCase = true) }
        val perType = catalogs.filterNot { catalog -> catalog.type.equals("all", ignoreCase = true) }
        return (allTypes + perType).distinctBy { catalog -> catalog.id.lowercase() }
    }

    /** The optional `config` array used by manifests that describe their own settings form. */
    private fun JsonObject.configFields(): List<AddonConfigField> =
        array("config").mapNotNull { element ->
            val field = element as? JsonObject ?: return@mapNotNull null
            val key = field.optionalString("key")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            AddonConfigField(
                key = key,
                type = field.optionalString("type")?.takeIf(String::isNotBlank),
                title = field.optionalString("title")?.takeIf(String::isNotBlank),
                description = field.optionalString("description")?.takeIf(String::isNotBlank),
                default = field.optionalString("default"),
                options = field.stringList("options"),
                required = field.boolean("required"),
            )
        }

    private fun JsonObject.resources(
        defaultTypes: List<String>,
        defaultPrefixes: List<String>,
    ): List<AddonResource> =
        array("resources").map { resource ->
            when (resource) {
                is JsonPrimitive -> AddonResource(
                    name = resource.contentOrNull.orEmpty(),
                    types = defaultTypes,
                    idPrefixes = defaultPrefixes,
                )

                else -> {
                    val obj = resource.jsonObject
                    AddonResource(
                        name = obj.requiredString("name"),
                        types = obj.stringList("types").ifEmpty { defaultTypes },
                        idPrefixes = obj.stringList("idPrefixes").ifEmpty { defaultPrefixes },
                    )
                }
            }
        }.filter { it.name.isNotBlank() }

    private fun JsonObject.catalogs(): List<AddonCatalog> =
        array("catalogs").map { catalogElement ->
            val catalog = catalogElement.jsonObject
            AddonCatalog(
                type = catalog.requiredString("type"),
                id = catalog.requiredString("id"),
                name = catalog.optionalString("name").orEmpty().ifBlank { catalog.requiredString("id") },
                extra = catalog.extraProperties(),
            )
        }

    /**
     * What a catalog accepts, in the three shapes manifests use: `extra` as objects (the current
     * schema), `extra` as plain names (older manifests), or `extraSupported`/`extraRequired` with no
     * `extra` at all (older still).
     *
     * Only the first shape was read, so every catalog written in either of the other two looked
     * like a catalog that takes nothing extra — no `search` among them — and was left out of the
     * search screen entirely, which is why a search only reached the few newest sources.
     */
    private fun JsonObject.extraProperties(): List<AddonExtraProperty> {
        val listed = array("extra").mapNotNull { element ->
            when (element) {
                is JsonObject -> element.optionalString("name")?.takeIf(String::isNotBlank)?.let { name ->
                    AddonExtraProperty(
                        name = name,
                        isRequired = element.boolean("isRequired"),
                        options = element.stringList("options"),
                        optionsLimit = element.int("optionsLimit"),
                    )
                }

                else -> element.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)?.let { name ->
                    AddonExtraProperty(name = name)
                }
            }
        }
        if (listed.isNotEmpty()) return listed

        val required = stringList("extraRequired").map { it.lowercase() }.toSet()
        return (stringList("extraSupported") + stringList("extraRequired"))
            .map { it.lowercase() }
            .distinct()
            .map { name -> AddonExtraProperty(name = name, isRequired = name in required) }
    }

    private fun JsonObject.behaviorHints(): AddonBehaviorHints {
        val hints = this["behaviorHints"]?.jsonObject ?: return AddonBehaviorHints()
        return AddonBehaviorHints(
            configurable = hints.boolean("configurable"),
            configurationRequired = hints.boolean("configurationRequired"),
            adult = hints.boolean("adult"),
            p2p = hints.boolean("p2p"),
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        optionalString(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException(
                runBlocking { getString(Res.string.addons_manifest_missing_field, name) },
            )

    private fun JsonObject.optionalString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.stringList(name: String): List<String> =
        array(name).mapNotNull { element ->
            element.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
        }

    private fun JsonObject.array(name: String): JsonArray =
        (this[name] as? JsonArray) ?: JsonArray(emptyList())

    private fun JsonObject.boolean(name: String): Boolean =
        this[name]?.jsonPrimitive?.booleanOrNull == true

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
}

private fun String.resolveAgainstManifest(manifestUrl: String): String =
    when {
        startsWith("http://") || startsWith("https://") || startsWith("data:") -> this
        startsWith("//") -> "https:$this"
        else -> {
            val manifestBase = manifestUrl.substringBefore("?").substringBeforeLast('/', "")
            if (startsWith('/')) {
                val origin = manifestBase.substringBefore("/", missingDelimiterValue = manifestBase)
                val schemeAndHost = if (origin.contains("://")) {
                    val scheme = origin.substringBefore("://")
                    val host = manifestBase.substringAfter("://").substringBefore("/")
                    "$scheme://$host"
                } else {
                    manifestBase
                }
                "$schemeAndHost$this"
            } else {
                "$manifestBase/$this"
            }
        }
    }
