package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Intermediate tour chapters: Lambda expressions with receiver, Properties, Null safety
// (smart/safe casts), and Libraries and APIs (opt-in).
class IntermediateRoutesTest {

    @Test
    fun `lambda with receiver builds a menu without qualifying the receiver`() = testApplication {
        application { module() }
        val response = client.get("/tour/lambdas-with-receiver")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"menu\":\"Breakfast\""), "got $body")
        assertTrue(body.contains("\"total_cents\":2350"), "expected 350 + 900 + 1100, got $body")
        assertTrue(body.contains("Breakfast: Coffee, Pancakes, Omelette"), "expected buildString, got $body")
    }

    @Test
    fun `properties route shows backing fields, extensions and delegates`() = testApplication {
        application { module() }
        val response = client.get("/tour/properties")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        // The custom setter trims and capitalizes through `field`.
        assertTrue(body.contains("\"backing_field_normalized_name\":\"Kodee\""), "got $body")
        assertTrue(body.contains("\"computed_property_initial\":\"K\""), "got $body")
        assertTrue(body.contains("\"extension_property_last_char\":\"n\""), "got $body")
        assertTrue(body.contains("\"lazy_second_read_is_cached\":true"), "got $body")
        assertTrue(body.contains("\"lazy_block_ran_once\":1"), "expected one lazy evaluation, got $body")
        assertTrue(body.contains("logLevel: INFO -> DEBUG"), "expected the observable callback, got $body")
        assertTrue(body.contains("logLevel: DEBUG -> TRACE"), "got $body")
    }

    @Test
    fun `smart casts route narrows types and recovers from bad casts`() = testApplication {
        application { module() }
        val response = client.get("/tour/smart-casts?value=luke")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("String of 4 chars, upper: LUKE"), "expected the is-String branch, got $body")
        assertTrue(body.contains("\"safe_cast_or_fallback\":\"LUKE\""), "got $body")
        assertTrue(body.contains("\"unsafe_cast_throws\":true"), "expected `as` to fail, got $body")
        assertTrue(body.contains("\"filter_not_null\":[\"alpha\",\"beta\"]"), "got $body")
        assertTrue(body.contains("\"map_not_null_lengths\":[5,4]"), "got $body")
        assertTrue(body.contains("falcon.test"), "expected the Elvis early return, got $body")
        assertTrue(body.contains("no email"), "expected the null branch, got $body")
    }

    @Test
    fun `smart casts route falls back to the Int default`() = testApplication {
        application { module() }
        val body = client.get("/tour/smart-casts").compactJson()
        assertTrue(body.contains("Int doubled to 42"), "expected the is-Int branch, got $body")
        assertTrue(body.contains("\"safe_cast_or_fallback\":\"not a String\""), "expected as? to yield null, got $body")
    }

    @Test
    fun `opt-in route calls an opt-in gated API and formats a Duration`() = testApplication {
        application { module() }
        val response = client.get("/tour/opt-in?text=kotlin")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"experimental_reverse\":\"niltok\""), "got $body")
        assertTrue(body.contains("\"unsigned_array\":[1,2,3]"), "expected uintArrayOf, got $body")
        assertTrue(body.contains("\"unsigned_max\":4294967295"), "expected UInt.MAX_VALUE, got $body")
        assertTrue(body.contains("\"duration_in_whole_minutes\":90"), "got $body")
        assertTrue(body.contains("PT1H30M0.500S"), "expected the ISO-8601 duration, got $body")
    }
}
