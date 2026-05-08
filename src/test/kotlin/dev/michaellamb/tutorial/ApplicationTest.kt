package dev.michaellamb.tutorial

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\""), "expected status field in $body")
        assertTrue(body.contains("\"ok\""), "expected ok value in $body")
    }

    @Test
    fun `data-class endpoint demonstrates copy`() = testApplication {
        application { module() }
        val response = client.get("/tour/data-class")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Han Solo"))
        assertTrue(body.contains("Chewbacca"))
    }

    @Test
    fun `extensions endpoint computes slug and primes`() = testApplication {
        application { module() }
        val response = client.get("/tour/extensions?text=Hello%20World&n=13")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"slug\""))
        assertTrue(body.contains("hello-world"))
        assertTrue(body.contains("\"is_prime\""))
    }
}
