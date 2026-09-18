package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Behaviour of the plugins installed in Application.module(), each verified through a real call. */
class PluginBehaviourTest {

    // ---- StatusPages -------------------------------------------------------------------------

    @Test
    fun `a thrown ApiException becomes its mapped status and an ErrorResponse body`() = testApplication {
        application { module() }

        val badId = client.get("/notes/not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, badId.status)
        assertTrue(badId.bodyAsText().contains("\"error\""), "expected the ErrorResponse shape")

        val missing = client.get("/notes/11111111-1111-1111-1111-111111111111")
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }

    @Test
    fun `require failures and undecodable bodies both become 400`() = testApplication {
        application { module() }

        // require(title.isNotBlank()) -> IllegalArgumentException
        val blankTitle = client.post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"","body":"x"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blankTitle.status)

        // ContentNegotiation cannot decode -> BadRequestException
        val malformed = client.post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":""")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    // ---- CallId ------------------------------------------------------------------------------

    @Test
    fun `a call id is generated when absent and echoed when supplied`() = testApplication {
        application { module() }

        val generated = client.get("/health").headers[HttpHeaders.XRequestId]
        assertNotNull(generated, "expected a generated correlation id")
        assertTrue(generated.isNotBlank())

        val supplied = client.get("/health") {
            header(HttpHeaders.XRequestId, "trace-abc")
        }.headers[HttpHeaders.XRequestId]
        assertEquals("trace-abc", supplied, "an inbound id should be preserved, not replaced")
    }

    // ---- CachingHeaders ----------------------------------------------------------------------

    @Test
    fun `widgets are cacheable and admin pages are not`() = testApplication {
        application { module() }

        val widget = client.get("/widgets/projects").headers[HttpHeaders.CacheControl]
        assertNotNull(widget)
        assertTrue(widget.contains("max-age=60"), "widget should advertise the 60s TTL, got: $widget")

        val admin = client.get("/admin/projects").headers[HttpHeaders.CacheControl]
        assertEquals("no-store", admin, "admin pages must never be cached")
    }

    // ---- CSRF --------------------------------------------------------------------------------

    @Test
    fun `admin form posts require a matching Origin`() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        // No Origin: the shape of a scripted POST, not a browser form submission.
        val noOrigin = client.post("/admin/projects") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody("name=x")
        }
        assertEquals(HttpStatusCode.Forbidden, noOrigin.status)

        // An Origin belonging to someone else.
        val foreignOrigin = client.post("/admin/projects") {
            contentType(ContentType.Application.FormUrlEncoded)
            header(HttpHeaders.Origin, "https://evil.example")
            header(HttpHeaders.Host, "localhost")
            setBody("name=x")
        }
        assertEquals(HttpStatusCode.Forbidden, foreignOrigin.status)
    }

    @Test
    fun `csrf does not apply outside the admin subtree`() = testApplication {
        application { module() }
        // POST /notes is called cross-origin by the blog and by Swagger's Try-It-Out, so a
        // route-scoped install is what keeps it working.
        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"from another origin","body":"b"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    // ---- Micrometer --------------------------------------------------------------------------

    @Test
    fun `metrics endpoint exposes ktor request timings`() = testApplication {
        application { module() }
        client.get("/health") // generate a sample
        val body = client.get("/metrics").bodyAsText()
        assertTrue(body.contains("ktor_http_server"), "expected Ktor server metrics in the scrape")
    }

    // ---- Resources ---------------------------------------------------------------------------

    @Test
    fun `type-safe routes decode their query string into the resource class`() = testApplication {
        application { module() }

        val filtered = client.get("/archive?tag=beginner").bodyAsText()
        assertTrue(filtered.contains("/tour/variables"), "beginner chapter should be listed")
        assertTrue(!filtered.contains("/tour/reflection"), "the filter should exclude other chapters")

        // A nested resource still sees its parent's query parameters.
        val chapter = client.get("/archive/beyond?tag=ignored").bodyAsText()
        assertTrue(chapter.contains("/tour/flow"))
        assertTrue(chapter.contains("ignored"), "nested resource should expose the inherited tag")
    }

    // ---- Authentication (JWT demo) -----------------------------------------------------------

    @Test
    fun `the protected demo route requires a valid bearer token`() = testApplication {
        application { module() }

        val anonymous = client.get("/demo-auth/whoami")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)

        val garbage = client.get("/demo-auth/whoami") {
            header(HttpHeaders.Authorization, "Bearer not.a.jwt")
        }
        assertEquals(HttpStatusCode.Unauthorized, garbage.status)

        val token = Json.parseToJsonElement(
            client.post("/demo-auth/token?user=kodee").bodyAsText(),
        ).jsonObject.getValue("token").jsonPrimitive.content

        val authorised = client.get("/demo-auth/whoami") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, authorised.status)
        assertTrue(authorised.bodyAsText().contains("kodee"))
    }

    @Test
    fun `a jwt's claims are readable without the signing secret`() = testApplication {
        application { module() }
        val token = Json.parseToJsonElement(
            client.post("/demo-auth/token?user=kodee").bodyAsText(),
        ).jsonObject.getValue("token").jsonPrimitive.content

        // The point of the demo: decoding needs no key, because a JWT is signed, not encrypted.
        val decoded = client.get("/demo-auth/decode?token=$token").bodyAsText()
        assertTrue(decoded.contains("kodee"), "claims should be readable by anyone holding the token")
        assertTrue(decoded.contains("HS256"))
    }
}
