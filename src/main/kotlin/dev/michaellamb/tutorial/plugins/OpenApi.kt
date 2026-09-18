package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.catalog.endpointSummaries
import dev.michaellamb.tutorial.catalog.operationIdFor
import dev.michaellamb.tutorial.catalog.tagFor
import dev.michaellamb.tutorial.catalog.tagOrder
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

/**
 * A serializeModel function for OpenApiDocSource.Routing that post-processes the generated spec:
 *  1. strips resolver-error descriptions (see above), and
 *  2. completes polymorphic schemas so Swagger's "Try it out" produces a valid body
 *     (see [completeDiscriminatorSubtypes] / [withSealedWhenExample]), and
 *  3. fills in the operationId, tag and summary the generator leaves empty
 *     (see [withOperationMetadata]), and
 *  4. documents the errors StatusPages produces outside the routing tree
 *     (see [withErrorResponses]).
 */
fun cleanedOpenApiSerializer(): (OpenApiDoc) -> String = { doc ->
    val tree = specJson.encodeToJsonElement(OpenApiDoc.serializer(), doc)
    val processed = tree
        .stripResolverErrors()
        .completeDiscriminatorSubtypes()
        .withSealedWhenExample()
        .withOperationMetadata()
        .withErrorResponses()
    specJson.encodeToString(JsonElement.serializer(), processed)
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

// The OpenAPI generator describes the `Shape` sealed interface as a correct `oneOf` plus a
// `discriminator(propertyName = "type")` — but it never declares the `type` property on the mapped
// subtypes (Circle/Square/Triangle). With the discriminator absent from each branch, Swagger UI
// builds its "Try it out" body from the first oneOf branch and omits "type" (e.g. {"radius": 0}),
// which then fails Jackson's @JsonTypeInfo deserialization with a 400. We walk every schema that
// carries a discriminator and inject the discriminator property — a single-value enum, marked
// required — into each subtype it maps to. This makes the schema semantically complete and keeps the
// rendered model accurate, all without touching the (intentionally untouched) tour route.
private fun JsonElement.completeDiscriminatorSubtypes(): JsonElement {
    val root = this as? JsonObject ?: return this
    val components = root["components"] as? JsonObject ?: return this
    val schemas = components["schemas"] as? JsonObject ?: return this

    // component name -> set of (discriminator property, discriminator value) to inject
    val injections = mutableMapOf<String, MutableSet<Pair<String, String>>>()
    for ((_, schema) in schemas) {
        val discriminator = (schema as? JsonObject)?.get("discriminator") as? JsonObject ?: continue
        val property = (discriminator["propertyName"] as? JsonPrimitive)?.content ?: continue
        val mapping = discriminator["mapping"] as? JsonObject ?: continue
        for ((value, ref) in mapping) {
            val name = (ref as? JsonPrimitive)?.content?.substringAfterLast('/') ?: continue
            injections.getOrPut(name) { mutableSetOf() }.add(property to value)
        }
    }
    if (injections.isEmpty()) return this

    val patched = buildJsonObject {
        for ((name, schema) in schemas) {
            val inject = injections[name]
            if (inject == null || schema !is JsonObject) put(name, schema)
            else put(name, schema.withDiscriminatorProperties(inject))
        }
    }
    return root.replacing("components", components.replacing("schemas", patched))
}

/** Adds each (property, value) as a required, single-value string enum on this object schema. */
private fun JsonObject.withDiscriminatorProperties(inject: Set<Pair<String, String>>): JsonObject {
    val existingProps = this["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val newProps = buildJsonObject {
        for ((k, v) in existingProps) put(k, v)
        for ((property, value) in inject) put(
            property,
            buildJsonObject {
                put("type", JsonPrimitive("string"))
                put("enum", buildJsonArray { add(JsonPrimitive(value)) })
            },
        )
    }
    // Preserve existing required entries (insertion order), then append the discriminator props.
    val required = LinkedHashSet((this["required"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content })
    inject.forEach { required.add(it.first) }
    return replacing("properties", newProps)
        .replacing("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
}

// A valid /tour/sealed-when body. The discriminator fix above already lets Swagger build a usable
// example from the Circle branch, but an explicit example guarantees the editor pre-fills a body
// that Jackson accepts (Swagger's oneOf example synthesis is otherwise unreliable).
private val SEALED_WHEN_EXAMPLE = buildJsonObject {
    put("type", JsonPrimitive("Circle"))
    put("radius", JsonPrimitive(3.0))
}

private fun JsonElement.withSealedWhenExample(): JsonElement {
    val root = this as? JsonObject ?: return this
    val paths = root["paths"] as? JsonObject ?: return this
    val path = paths["/tour/sealed-when"] as? JsonObject ?: return this
    val post = path["post"] as? JsonObject ?: return this
    val requestBody = post["requestBody"] as? JsonObject ?: return this
    val content = requestBody["content"] as? JsonObject ?: return this
    val media = content["application/json"] as? JsonObject ?: return this
    if (media.containsKey("example")) return this

    val newMedia = media.replacing("example", SEALED_WHEN_EXAMPLE)
    return root.replacing(
        "paths",
        paths.replacing(
            "/tour/sealed-when",
            path.replacing(
                "post",
                post.replacing(
                    "requestBody",
                    requestBody.replacing("content", content.replacing("application/json", newMedia)),
                ),
            ),
        ),
    )
}

/** Returns a copy with [key] set to [value] — overwriting in place (order preserved) or appending. */
private fun JsonObject.replacing(key: String, value: JsonElement): JsonObject = buildJsonObject {
    for ((k, v) in this@replacing) put(k, v)
    put(key, value)
}

// The OpenAPI compiler plugin only emits `operationId` and `tag` for a route that carries an
// explicit KDoc annotation, and it leaves `summary` as "". Annotating 40-odd routes would put
// OpenAPI boilerplate above every teaching handler — the same objection that motivated
// stripResolverErrors above — so we fill all three in here instead, from the one catalog the home
// page already renders (catalog/EndpointCatalog.kt).
//
// Without this every operation lands in swagger-ui's synthetic "default" bucket: one flat list of
// 50 bare paths with no prose and no stable anchor to link at.
private val HTTP_METHODS = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

private fun JsonElement.withOperationMetadata(): JsonElement {
    val root = this as? JsonObject ?: return this
    val paths = root["paths"] as? JsonObject ?: return this

    val patchedPaths = buildJsonObject {
        for ((path, pathItem) in paths) {
            if (pathItem !is JsonObject) {
                put(path, pathItem)
                continue
            }
            put(
                path,
                buildJsonObject {
                    for ((key, operation) in pathItem) {
                        // A path item also holds non-operation keys (`parameters`, `summary`, `$ref`).
                        if (key.lowercase() !in HTTP_METHODS || operation !is JsonObject) {
                            put(key, operation)
                        } else {
                            put(key, operation.withMetadataFor(key, path))
                        }
                    }
                },
            )
        }
    }

    // Swagger UI orders its sections by the document-level `tags` array, so declaring it here makes
    // the docs page mirror the home page's section order instead of sorting by first appearance.
    val tags = buildJsonArray {
        for ((name, description) in tagOrder) {
            add(
                buildJsonObject {
                    put("name", JsonPrimitive(name))
                    put("description", JsonPrimitive(description))
                },
            )
        }
    }

    return root.replacing("paths", patchedPaths).replacing("tags", tags)
}

private fun JsonObject.withMetadataFor(method: String, path: String): JsonObject {
    var result = replacing("operationId", JsonPrimitive(operationIdFor(method, path)))
        .replacing(
            "tags",
            buildJsonArray { add(JsonPrimitive(tagFor(path))) },
        )

    // Only fill a summary that isn't already there — a real @summary annotation should win.
    val existing = (this["summary"] as? JsonPrimitive)?.content
    if (existing.isNullOrBlank()) {
        endpointSummaries[method.uppercase() to path]?.let {
            result = result.replacing("summary", JsonPrimitive(it))
        }
    }
    return result
}

// plugins/StatusPages.kt answers with an ErrorResponse for any failure, but it does so *outside* the
// routing tree — so the compiler plugin, which infers responses from the call.respond in each
// handler, cannot see it. Since the refactor that moved /notes onto thrown ApiExceptions, those
// handlers no longer respond 400/404 themselves either.
//
// OpenAPI's `default` response means exactly "any status not listed above", which is the honest
// description of a global error handler, so every operation gets one. The ErrorResponse schema has
// to be injected too: nothing in the routing tree returns the type, so the generator never emits it.
private const val ERROR_SCHEMA = "ErrorResponse"

private fun JsonElement.withErrorResponses(): JsonElement {
    val root = this as? JsonObject ?: return this
    val paths = root["paths"] as? JsonObject ?: return this

    val patchedPaths = buildJsonObject {
        for ((path, pathItem) in paths) {
            if (pathItem !is JsonObject) {
                put(path, pathItem)
                continue
            }
            put(
                path,
                buildJsonObject {
                    for ((key, operation) in pathItem) {
                        if (key.lowercase() !in HTTP_METHODS || operation !is JsonObject) {
                            put(key, operation)
                        } else {
                            put(key, operation.withDefaultErrorResponse())
                        }
                    }
                },
            )
        }
    }

    val components = root["components"] as? JsonObject ?: JsonObject(emptyMap())
    val schemas = components["schemas"] as? JsonObject ?: JsonObject(emptyMap())
    val withError = schemas.replacing(
        ERROR_SCHEMA,
        buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("title", JsonPrimitive(ERROR_SCHEMA))
            put("required", buildJsonArray { add(JsonPrimitive("error")) })
            put(
                "properties",
                buildJsonObject {
                    put("error", buildJsonObject { put("type", JsonPrimitive("string")) })
                },
            )
        },
    )

    return root
        .replacing("paths", patchedPaths)
        .replacing("components", components.replacing("schemas", withError))
}

private fun JsonObject.withDefaultErrorResponse(): JsonObject {
    val responses = this["responses"] as? JsonObject ?: JsonObject(emptyMap())
    if (responses.containsKey("default")) return this

    val default = buildJsonObject {
        put("description", JsonPrimitive("Error handled by StatusPages (400, 404, 409 or 500)."))
        put(
            "content",
            buildJsonObject {
                put(
                    "application/json",
                    buildJsonObject {
                        put(
                            "schema",
                            buildJsonObject { put("\$ref", JsonPrimitive("#/components/schemas/$ERROR_SCHEMA")) },
                        )
                    },
                )
            },
        )
    }
    return replacing("responses", responses.replacing("default", default))
}
