package dev.michaellamb.tutorial.home

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeRoutesTest {

    @Test
    fun `home page returns html`() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.headers[HttpHeaders.ContentType]
        assertNotNull(contentType, "expected Content-Type header")
        assertTrue(
            contentType.startsWith(ContentType.Text.Html.toString()),
            "expected text/html, got $contentType",
        )
    }

    @Test
    fun `home page lists every route`() = testApplication {
        application { module() }
        val body = client.get("/").bodyAsText()
        listOf(
            // Beginner tour
            "/tour/variables",
            "/tour/basic-types",
            "/tour/collection-types",
            "/tour/control-flow",
            "/tour/functions",
            "/tour/data-class",
            "/tour/null-safety",
            // Intermediate tour
            "/tour/extensions",
            "/tour/scope-functions",
            "/tour/lambdas-with-receiver",
            "/tour/interfaces",
            "/tour/delegation",
            "/tour/objects",
            "/tour/open-classes",
            "/tour/sealed-when",
            "/tour/enums",
            "/tour/value-classes",
            "/tour/properties",
            "/tour/smart-casts",
            "/tour/opt-in",
            // Beyond the tour
            "/tour/collections",
            "/tour/higher-order-functions",
            "/tour/sequences",
            "/tour/coroutines",
            "/tour/result",
            "/tour/generics",
            "/tour/reflection",
            "/widgets/letterboxd",
            "/widgets/steam",
            "/widgets/cluster",
            "/notes",
            "/health",
        ).forEach { path ->
            assertTrue(body.contains(path), "expected homepage to mention $path")
        }
    }

    @Test
    fun `home page is a complete document`() = testApplication {
        application { module() }
        val body = client.get("/").bodyAsText()
        assertTrue(body.startsWith("<!DOCTYPE html>"), "expected HTML5 doctype prefix")
        assertTrue(body.contains("<html"), "expected <html> tag")
        assertTrue(body.contains("</html>"), "expected closing </html> tag")
    }

    @Test
    fun `each card deep-links to its own operation in the swagger ui`() = testApplication {
        application { module() }
        val body = client.get("/").bodyAsText()
        val spec = Json.parseToJsonElement(client.get("/swagger/documentation.yaml").bodyAsText()).jsonObject

        // Derive the expected href from the spec the service actually serves rather than hard-coding
        // it, so this fails if the two surfaces ever drift apart — including if a Ktor bump changes
        // how ids are generated.
        fun expectedHref(path: String, method: String): String {
            val operation = spec.getValue("paths").jsonObject.getValue(path).jsonObject.getValue(method).jsonObject
            val tag = operation.getValue("tags").jsonArray.single().jsonPrimitive.content
            val operationId = operation.getValue("operationId").jsonPrimitive.content
            return "/swagger?docExpansion=none#/$tag/$operationId"
        }

        // A tour card, whose card path additionally carries a demo query string the spec doesn't.
        assertTrue(
            body.contains(expectedHref("/tour/variables", "get")),
            "expected the /tour/variables card to link at its own swagger operation",
        )
        // A widget card.
        assertTrue(
            body.contains(expectedHref("/widgets/steam", "get")),
            "expected the /widgets/steam card to link at its own swagger operation",
        )
        // The health card.
        assertTrue(
            body.contains(expectedHref("/health", "get")),
            "expected the /health card to link at its own swagger operation",
        )
        // Notes is a form rather than a set of cards, so it links at the whole section instead.
        assertTrue(
            body.contains("/swagger?docExpansion=none#/Notes\""),
            "expected the Notes section to link at its swagger tag",
        )
    }

    @Test
    fun `the working sections lead the page and the tour follows`() = testApplication {
        application { module() }
        val body = client.get("/").bodyAsText()
        // The shiny, working parts own the top; the teaching material is the reward for scrolling.
        val order = listOf("notes", "widgets", "tour", "health").map { body.indexOf("id=\"$it\"") }
        assertTrue(order.none { it < 0 }, "expected all four sections to render")
        assertEquals(order.sorted(), order, "expected section order notes, widgets, tour, health")
        // The header nav mirrors it.
        val nav = listOf("#notes", "#widgets", "#tour", "#health").map { body.indexOf("href=\"$it\"") }
        assertEquals(nav.sorted(), nav, "expected nav order to mirror the sections")
    }
}
