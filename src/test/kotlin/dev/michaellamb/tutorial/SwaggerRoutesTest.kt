package dev.michaellamb.tutorial

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import dev.michaellamb.tutorial.catalog.tagOrder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwaggerRoutesTest {

    @Test
    fun `swagger ui page is served`() = testApplication {
        application { module() }
        val response = client.get("/swagger")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("swagger-ui", ignoreCase = true), "expected Swagger UI markup")
    }

    @Test
    fun `generated openapi spec documents the routes`() = testApplication {
        application { module() }
        val spec = client.get("/swagger/documentation.yaml").bodyAsText()
        // The spec is generated from the routing tree by the ktor { openApi } compiler plugin.
        // A few representative paths across the route groups must be present.
        listOf("/health", "/notes", "/notes/{id}", "/tour/sealed-when").forEach { path ->
            assertTrue(spec.contains("\"$path\""), "expected spec to document $path")
        }
        // Typed DTOs resolve to real component schemas.
        assertTrue(spec.contains("CreateNoteRequest"), "expected CreateNoteRequest schema")
    }

    @Test
    fun `generated spec carries no schema-resolver errors`() = testApplication {
        application { module() }
        val spec = client.get("/swagger/documentation.yaml").bodyAsText()
        // plugins/OpenApi.kt strips the kotlinx "Failed to resolve schema for Map" noise that the
        // compiler plugin bakes into the dynamic /tour responses.
        assertFalse(
            spec.contains("Failed to resolve schema"),
            "resolver-error descriptions should be stripped from the spec",
        )
    }

    @Test
    fun `sealed-when request body is deserializable from swagger`() = testApplication {
        application { module() }
        val spec = client.get("/swagger/documentation.yaml").bodyAsText()
        // plugins/OpenApi.kt injects the missing "type" discriminator into each Shape subtype so the
        // schema is complete and Swagger builds a body Jackson accepts.
        assertTrue(spec.contains("\"enum\":[\"Circle\"]"), "Circle subtype should declare its discriminator value")
        assertTrue(spec.contains("\"enum\":[\"Square\"]"), "Square subtype should declare its discriminator value")
        assertTrue(spec.contains("\"enum\":[\"Triangle\"]"), "Triangle subtype should declare its discriminator value")
        // ...and pins an explicit, valid example onto the request body.
        assertTrue(
            spec.contains("\"example\":{\"type\":\"Circle\",\"radius\":3.0}"),
            "sealed-when request body should carry a deserializable example",
        )
    }

    @Test
    fun `swagger ui is served with deep linking enabled`() = testApplication {
        application { module() }
        val body = client.get("/swagger").bodyAsText()
        // Without this the /swagger#/Tag/operationId anchors the home page builds are inert:
        // swagger-ui simply ignores the fragment. See catalog/EndpointCatalog.kt#swaggerHref.
        assertTrue(body.contains("deepLinking: true"), "expected deep linking to be enabled")
    }

    @Test
    fun `every operation carries a unique operationId, a tag and a summary`() = testApplication {
        application { module() }
        val spec = client.get("/swagger/documentation.yaml").bodyAsText()
        val operations = operationsIn(spec)
        assertTrue(operations.isNotEmpty(), "expected the spec to document some operations")

        // The compiler plugin leaves all three empty unless a route is annotated; plugins/OpenApi.kt
        // fills them from the shared catalog. This is the check that catches a new route being
        // added without a catalog entry.
        operations.forEach { (name, operation) ->
            assertTrue(
                (operation["operationId"] as? JsonPrimitive)?.content?.isNotBlank() == true,
                "$name should carry an operationId",
            )
            assertTrue(
                operation["tags"]?.jsonArray?.isNotEmpty() == true,
                "$name should carry a tag",
            )
            assertTrue(
                (operation["summary"] as? JsonPrimitive)?.content?.isNotBlank() == true,
                "$name should carry a summary",
            )
        }

        // OpenAPI requires distinct operationIds and swagger-ui resolves deep links by id, so a
        // collision would silently point two home-page cards at the same anchor. operationIdFor
        // collapses '/', '-', '.' and '{}' all to '_', which is exactly how that could happen.
        val ids = operations.map { (_, operation) -> operation.getValue("operationId").jsonPrimitive.content }
        assertEquals(ids.size, ids.toSet().size, "operationIds must be unique; got duplicates in $ids")
    }

    @Test
    fun `operations are tagged into the sections the home page links at`() = testApplication {
        application { module() }
        val spec = client.get("/swagger/documentation.yaml").bodyAsText()
        val root = Json.parseToJsonElement(spec).jsonObject
        val paths = root.getValue("paths").jsonObject

        fun tagOf(path: String, method: String) =
            paths.getValue(path).jsonObject.getValue(method).jsonObject
                .getValue("tags").jsonArray.single().jsonPrimitive.content

        assertEquals("Tour", tagOf("/tour/variables", "get"))
        assertEquals("Notes", tagOf("/notes/{id}", "delete"))
        assertEquals("Widgets", tagOf("/widgets/steam", "get"))
        assertEquals("Service", tagOf("/health", "get"))

        assertEquals(
            "get_tour_variables",
            paths.getValue("/tour/variables").jsonObject.getValue("get").jsonObject
                .getValue("operationId").jsonPrimitive.content,
        )

        // Swagger UI orders its sections by the document-level tags array, so this is what makes the
        // docs page mirror the home page's section order.
        // Compare against the catalog rather than a literal, so adding a tag needs one edit, not two.
        val declaredTags = root.getValue("tags").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(tagOrder.map { it.first }, declaredTags)
    }

    private companion object {
        val HTTP_METHODS = setOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

        /** Every (label, operation) pair in the spec, skipping non-operation path-item keys. */
        fun operationsIn(spec: String): List<Pair<String, JsonObject>> =
            Json.parseToJsonElement(spec).jsonObject.getValue("paths").jsonObject.flatMap { (path, item) ->
                item.jsonObject.entries
                    .filter { it.key.lowercase() in HTTP_METHODS }
                    .map { "${it.key.uppercase()} $path" to it.value.jsonObject }
            }
    }
}
