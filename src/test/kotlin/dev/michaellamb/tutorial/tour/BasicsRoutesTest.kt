package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Beginner tour chapters: Hello world (variables + string templates), Basic types,
// Collections (the three types), Control flow, Functions.
class BasicsRoutesTest {

    @Test
    fun `variables route interpolates templates and reports inferred types`() = testApplication {
        application { module() }
        val response = client.get("/tour/variables?name=Kodee")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("Hello, Kodee!"), "expected string template output, got $body")
        assertTrue(body.contains("Kodee has 5 characters"), "expected template expression, got $body")
        assertTrue(body.contains("\"inferred_type\":\"Int\""), "expected inferred Int, got $body")
        assertTrue(body.contains("\"declared_type\":\"Long\""), "expected declared Long, got $body")
        assertTrue(body.contains("\"var_after_two_increments\":2"), "expected var reassignment, got $body")
    }

    @Test
    fun `basic types route shows inference and explicit widening`() = testApplication {
        application { module() }
        val response = client.get("/tour/basic-types")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"100\":\"Int\""), "expected Int inference, got $body")
        assertTrue(body.contains("\"100L\":\"Long\""), "expected Long inference, got $body")
        assertTrue(body.contains("\"3.14\":\"Double\""), "expected Double inference, got $body")
        assertTrue(body.contains("\"'K'\":\"Char\""), "expected Char inference, got $body")
        assertTrue(body.contains("\"explicit_widening\":200"), "expected 100 + 100L, got $body")
        assertTrue(body.contains("\"char_code\":75"), "expected 'K'.code, got $body")
    }

    @Test
    fun `collection types route separates read-only from mutable`() = testApplication {
        application { module() }
        val response = client.get("/tour/collection-types")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"mutable_list_after_add_and_remove\":[\"green\",\"blue\",\"yellow\"]"), "got $body")
        assertTrue(body.contains("\"set_size\":3"), "expected the duplicate to collapse, got $body")
        assertTrue(body.contains("\"map_lookup_hit\":190"), "expected map lookup, got $body")
        assertTrue(body.contains("\"map_lookup_miss_is_null\":null"), "expected null for a missing key, got $body")
    }

    @Test
    fun `control flow route evaluates ifs, whens, ranges and loops`() = testApplication {
        application { module() }
        val response = client.get("/tour/control-flow?n=7")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"if_expression_parity\":\"odd\""), "got $body")
        assertTrue(body.contains("\"when_expression_size\":\"a handful\""), "expected the 2..9 range arm, got $body")
        assertTrue(body.contains("\"when_without_subject\":\"positive odd\""), "got $body")
        assertTrue(body.contains("\"half_open_range_1_until_5\":[1,2,3,4]"), "expected ..< to exclude 5, got $body")
        assertTrue(body.contains("\"for_squares\":[1,4,9,16,25]"), "got $body")
        assertTrue(body.contains("\"for_downto_step_2\":[5,3,1]"), "got $body")
        assertTrue(body.contains("\"while_doublings_to_reach_n\":3"), "expected 1->2->4->8, got $body")
    }

    @Test
    fun `functions route exercises defaults, named arguments and early returns`() = testApplication {
        application { module() }
        val response = client.get("/tour/functions?name=Han%20Solo")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"all_defaults\":\"Hello, Han Solo.\""), "got $body")
        assertTrue(body.contains("\"named_argument_skips_middle\":\"Hello, Han Solo!\""), "got $body")
        assertTrue(body.contains("\"named_arguments_reordered\":\"Howdy, Han Solo.\""), "got $body")
        assertTrue(body.contains("\"single_expression_square\":81"), "got $body")
        assertTrue(body.contains("\"early_return_initials\":\"HS\""), "got $body")
        assertTrue(body.contains("\"early_return_on_blank\":\"??\""), "expected the guard clause, got $body")
    }
}
