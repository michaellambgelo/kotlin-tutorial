package dev.michaellamb.tutorial.plugins

import io.ktor.openapi.OpenApiDoc
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

// The OpenAPI compiler plugin bakes an inline response schema for each /tour route from its
// `Map<String, Any>` return type. kotlinx can't resolve `Any`, so it embeds a generic object tagged
// with a noisy "Failed to resolve schema for Map..." description. Those teaching responses are
// intentionally dynamic — one map per language feature — so rather than annotate 13 routes (and
// pollute the pedagogy), we post-process the assembled spec once via the source's serializeModel
// hook, dropping any description that is really an internal resolver error. Typed @Serializable DTOs
// (Note, Shape, HealthResponse, ...) resolve cleanly and pass through untouched.
//
// Null/default omission mirrors the spec serializer so the output stays a compact, valid document.
private val specJson = Json {
    explicitNulls = false
    encodeDefaults = false
}

/** A serializeModel function for OpenApiDocSource.Routing that strips resolver-error descriptions. */
fun cleanedOpenApiSerializer(): (OpenApiDoc) -> String = { doc ->
    val tree = specJson.encodeToJsonElement(OpenApiDoc.serializer(), doc)
    specJson.encodeToString(JsonElement.serializer(), tree.stripResolverErrors())
}

private fun JsonElement.stripResolverErrors(): JsonElement = when (val element = this) {
    is JsonObject -> buildJsonObject {
        for ((key, value) in element) {
            val isResolverError = key == "description" &&
                value is JsonPrimitive && value.content.startsWith("Failed to resolve schema")
            if (!isResolverError) put(key, value.stripResolverErrors())
        }
    }
    is JsonArray -> buildJsonArray {
        for (item in element) add(item.stripResolverErrors())
    }
    else -> element
}
