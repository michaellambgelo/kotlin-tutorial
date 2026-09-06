package dev.michaellamb.tutorial.tour

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Intermediate tour chapters: Objects, Open and special classes (open/abstract, enum,
// inline value), and Classes and interfaces (delegation).
class ObjectsAndClassesRoutesTest {

    @Test
    fun `objects route covers singletons, data objects and companions`() = testApplication {
        application { module() }
        val response = client.get("/tour/objects")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        // data object derives toString() from its name; a plain object does not.
        assertTrue(body.contains("\"data_object_to_string\":\"AppConfig\""), "got $body")
        assertTrue(body.contains("\"plain_object_to_string_is_default\":true"), "got $body")
        assertTrue(body.contains("\"companion_from_fahrenheit\":100.0"), "expected 212F == 100C, got $body")
        assertTrue(body.contains("\"object_expression_sort\":[5,4,3,1,1]"), "got $body")
    }

    @Test
    fun `singleton state survives between requests`() = testApplication {
        application { module() }
        val first = client.get("/tour/objects").json()
        val second = client.get("/tour/objects").json()
        // Each request registers twice against the one process-wide instance, so the next
        // request picks up exactly where the previous one left off.
        assertEquals(
            first["registry_count_after_first_register"].asInt() + 1,
            first["registry_count_after_second_register"].asInt(),
        )
        assertEquals(
            first["registry_count_after_first_register"].asInt() + 2,
            second["registry_count_after_first_register"].asInt(),
        )
    }

    @Test
    fun `open classes route dispatches through the abstract parent`() = testApplication {
        application { module() }
        val response = client.get("/tour/open-classes")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("Sedan has 4 wheels and goes vroom"), "expected inherited describe(), got $body")
        assertTrue(body.contains("Formula has 4 wheels and goes VROOOOM (at speed)"), "expected super call, got $body")
        assertTrue(body.contains("Scrambler has 2 wheels and goes brap"), "got $body")
        assertTrue(body.contains("\"race_car_is_a_car\":true"), "got $body")
        assertTrue(body.contains("\"motorcycle_is_a_car\":false"), "got $body")
    }

    @Test
    fun `enums route carries per-constant state and an exhaustive when`() = testApplication {
        application { module() }
        val response = client.get("/tour/enums?planet=jupiter")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"selected\":\"JUPITER\""), "got $body")
        assertTrue(body.contains("\"ordinal\":2"), "got $body")
        assertTrue(body.contains("there is no ground"), "expected the exhaustive when arm, got $body")
        assertTrue(body.contains("jupiter — big and stormy"), "expected the per-constant override, got $body")
        assertTrue(body.contains("\"weight_of_70kg\":1735.3"), "expected 70 * 24.79, got $body")
    }

    @Test
    fun `enums route falls back to EARTH for an unknown name`() = testApplication {
        application { module() }
        val body = client.get("/tour/enums?planet=pluto").compactJson()
        assertTrue(body.contains("\"selected\":\"EARTH\""), "got $body")
        assertTrue(body.contains("you are here"), "got $body")
    }

    @Test
    fun `value classes route keeps equality by value and validates in init`() = testApplication {
        application { module() }
        val response = client.get("/tour/value-classes")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("inviting u-1138 at han@falcon.test"), "got $body")
        assertTrue(body.contains("\"email_domain\":\"falcon.test\""), "got $body")
        assertTrue(body.contains("\"equality_is_by_value\":true"), "got $body")
        assertTrue(body.contains("id must not be blank"), "expected the init-block require(), got $body")
    }

    @Test
    fun `delegation route forwards unoverridden members to the delegate`() = testApplication {
        application { module() }
        val response = client.get("/tour/delegation")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.compactJson()
        assertTrue(body.contains("\"red_draw_overridden\":\"drawing circle in red\""), "got $body")
        assertTrue(body.contains("\"red_erase_delegated\":\"erasing corner\""), "expected forwarded erase(), got $body")
        // Delegation forwards calls; it does not rewire the delegate's own `this`.
        assertTrue(body.contains("\"red_info_from_delegate\":\"PenTool(color=black)\""), "got $body")
    }
}
