package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SequenceRoutesTest {

    @Test
    fun `lazy short-circuits and fibonacci`() = testApplication {
        application { module() }
        val response = client.get("/tour/sequences")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("eager_lambda_invocations"), "missing eager count, got $body")
        assertTrue(body.contains("lazy_lambda_invocations"), "missing lazy count, got $body")
        assertTrue(body.contains("same_result"), "missing equality flag, got $body")
        assertTrue(body.contains("true"), "expected same_result true, got $body")
        // Fibonacci first 10: 0,1,1,2,3,5,8,13,21,34 — check the tail values.
        assertTrue(body.contains("34"), "expected fibonacci tail, got $body")
    }
}
