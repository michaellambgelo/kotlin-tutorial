package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericsRoutesTest {

    @Test
    fun `generics route reports max and reified filtering`() = testApplication {
        application { module() }
        val response = client.get("/tour/generics")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("max_of_ints"), "missing key, got $body")
        assertTrue(body.contains("41"), "expected max int 41, got $body")
        assertTrue(body.contains("snow crash"), "expected max string, got $body")
        assertTrue(body.contains("only_ints_from_mixed"), "missing reified filter key, got $body")
    }
}
