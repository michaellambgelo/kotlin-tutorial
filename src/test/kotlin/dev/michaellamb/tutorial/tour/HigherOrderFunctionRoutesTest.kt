package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HigherOrderFunctionRoutesTest {

    @Test
    fun `composition and method reference`() = testApplication {
        application { module() }
        val response = client.get("/tour/higher-order-functions?x=10")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        // double(10)=20, then +1 = 21
        assertTrue(body.contains("21"), "expected composed result 21, got $body")
        // applyN(3, double, 10) = 80
        assertTrue(body.contains("80"), "expected applyN result 80, got $body")
        assertTrue(body.contains("LUKE"), "expected uppercased method-ref output, got $body")
    }

    @Test
    fun `defaults when no query`() = testApplication {
        application { module() }
        val response = client.get("/tour/higher-order-functions")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
