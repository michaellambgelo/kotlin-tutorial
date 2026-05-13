package dev.michaellamb.tutorial.home

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
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
            "/tour/data-class",
            "/tour/null-safety",
            "/tour/sealed-when",
            "/tour/coroutines",
            "/tour/extensions",
            "/tour/scope-functions",
            "/tour/collections",
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
}
