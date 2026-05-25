package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflectionRoutesTest {

    @Test
    fun `reflects class name and property values`() = testApplication {
        application { module() }
        val response = client.get("/tour/reflection")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Droid"), "expected class name, got $body")
        assertTrue(body.contains("is_data_class"), "missing isData key, got $body")
        // memberProperties walk surfaces both property values (proves kotlin-reflect loaded).
        assertTrue(body.contains("R2-D2"), "expected id property value, got $body")
        assertTrue(body.contains("astromech"), "expected model property value, got $body")
        assertTrue(body.contains("model_via_callable_ref"), "missing callable ref key, got $body")
    }
}
