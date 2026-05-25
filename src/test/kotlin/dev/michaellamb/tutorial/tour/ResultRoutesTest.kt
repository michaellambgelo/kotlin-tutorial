package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultRoutesTest {

    @Test
    fun `success path folds to ok`() = testApplication {
        application { module() }
        val response = client.get("/tour/result?n=16")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ok:"), "expected success fold, got $body")
        assertTrue(body.contains("recovered_to_nan"), "missing recovery key, got $body")
        // Batch includes a failure for -4 with the custom exception message.
        assertTrue(body.contains("negative number: -4"), "expected custom exception in batch, got $body")
    }

    @Test
    fun `negative input folds to error`() = testApplication {
        application { module() }
        val response = client.get("/tour/result?n=-9")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("error:"), "expected error fold, got $body")
        assertTrue(body.contains("negative number: -9"), "expected custom exception message, got $body")
    }
}
