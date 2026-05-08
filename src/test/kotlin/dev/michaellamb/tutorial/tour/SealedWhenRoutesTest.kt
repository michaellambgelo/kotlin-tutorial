package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SealedWhenRoutesTest {

    @Test
    fun `circle area`() = testApplication {
        application { module() }
        val response = client.post("/tour/sealed-when") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"Circle","radius":3.0}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"area\""))
        assertTrue(body.contains("28.27") || body.contains("28.274"), "expected ~pi*9 area, got $body")
    }

    @Test
    fun `square area`() = testApplication {
        application { module() }
        val response = client.post("/tour/sealed-when") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"Square","side":4.0}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("16"))
    }

    @Test
    fun `unknown shape returns 400`() = testApplication {
        application { module() }
        val response = client.post("/tour/sealed-when") {
            contentType(ContentType.Application.Json)
            setBody("""{"type":"Hexagon","side":1.0}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
