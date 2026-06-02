package dev.michaellamb.tutorial

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
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
}
