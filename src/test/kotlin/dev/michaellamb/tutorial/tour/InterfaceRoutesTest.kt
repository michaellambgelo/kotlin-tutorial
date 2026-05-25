package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterfaceRoutesTest {

    @Test
    fun `polymorphic dispatch and default method`() = testApplication {
        application { module() }
        val response = client.get("/tour/interfaces")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Rex says Woof"), "expected default describe(), got $body")
        assertTrue(body.contains("Milo says Meow"), "expected dynamic dispatch, got $body")
        // Cow overrides the default describe()
        assertTrue(body.contains("Bessie the cow goes Moo"), "expected overridden default, got $body")
    }
}
