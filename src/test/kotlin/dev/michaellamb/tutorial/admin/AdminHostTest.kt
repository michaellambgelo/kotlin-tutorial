package dev.michaellamb.tutorial.admin

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.michaellamb.tutorial.auth.CF_ACCESS_HEADER
import dev.michaellamb.tutorial.auth.CloudflareAccess
import dev.michaellamb.tutorial.plugins.configureAuthentication
import dev.michaellamb.tutorial.plugins.configureCors
import dev.michaellamb.tutorial.plugins.configureDependencies
import dev.michaellamb.tutorial.plugins.configureHttp
import dev.michaellamb.tutorial.plugins.configureRouting
import dev.michaellamb.tutorial.plugins.configureSerialization
import dev.michaellamb.tutorial.plugins.configureStatusPages
import dev.michaellamb.tutorial.plugins.configureStreaming
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The admin portal on its own hostname: host-scoped routing, the mount-relative forms, and the
 * Access token check. None of these hit the network — GET /notes on the admin host would call the
 * live now-store, so precedence is proven through the Access gate and a no-op POST instead.
 */
class AdminHostTest {

    @TempDir
    lateinit var tmp: File

    @BeforeTest
    fun setDbPaths() {
        System.setProperty("projects.db.path", File(tmp, "projects-${UUID.randomUUID()}.db").absolutePath)
        System.setProperty("notes.db.path", File(tmp, "notes-${UUID.randomUUID()}.db").absolutePath)
    }

    @AfterTest
    fun clearDbPaths() {
        System.clearProperty("projects.db.path")
        System.clearProperty("notes.db.path")
    }

    private companion object {
        const val ADMIN = "admin.test"
        const val ISSUER = "https://team.cloudflareaccess.com"
        const val AUD = "test-aud"

        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val signing: Algorithm = Algorithm.RSA256(keys.public as RSAPublicKey, keys.private as RSAPrivateKey)
        val access = CloudflareAccess(
            CloudflareAccess.verifierFor(Algorithm.RSA256(keys.public as RSAPublicKey, null), ISSUER, AUD),
        )

        fun token(
            aud: String = AUD,
            issuer: String = ISSUER,
            expiresAt: Instant = Instant.now().plusSeconds(600),
            alg: Algorithm = signing,
        ): String = JWT.create().withIssuer(issuer).withAudience(aud).withExpiresAt(expiresAt).sign(alg)
    }

    private fun ApplicationTestBuilder.mount(withAccess: Boolean = false) {
        val a = if (withAccess) access else null
        // Application.module()'s install list, with the admin host and Access check pinned.
        application {
            configureDependencies()
            configureStatusPages()
            configureSerialization()
            configureHttp(adminHost = ADMIN)
            configureCors(adminHost = ADMIN)
            configureAuthentication(a)
            configureStreaming()
            configureRouting(a, adminHost = ADMIN)
        }
    }

    private fun HttpRequestBuilder.on(host: String, jwt: String? = null) {
        header(HttpHeaders.Host, host)
        jwt?.let { header(CF_ACCESS_HEADER, it) }
    }

    private suspend fun ApplicationTestBuilder.getOn(path: String, host: String, jwt: String? = null): HttpResponse =
        client.get(path) { on(host, jwt) }

    // ---- host-scoped routing ----

    @Test
    fun `admin host root is the hub, public root is still the tutorial home`() = testApplication {
        mount()
        val hub = getOn("/", ADMIN).bodyAsText()
        assertTrue("Every admin tool" in hub, "admin host / should render the hub")
        assertTrue("href=\"/notes\"" in hub && "href=\"/projects\"" in hub)

        val home = getOn("/", "localhost").bodyAsText()
        assertFalse("Every admin tool" in home, "public / must not be the hub")
    }

    @Test
    fun `public notes is still the JSON API`() = testApplication {
        mount(withAccess = true)
        val response = getOn("/notes", "localhost")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }

    @Test
    fun `admin host notes resolves to the admin form, not the API`() = testApplication {
        // With the Access check on and no token, the admin route answers 403. The public /notes API
        // has no gate and would answer 200 — so a 403 proves which route the admin host resolved.
        mount(withAccess = true)
        assertEquals(HttpStatusCode.Forbidden, getOn("/notes", ADMIN).status)
    }

    @Test
    fun `admin host projects form posts and links within the portal layout`() = testApplication {
        mount()
        val page = getOn("/projects", ADMIN).bodyAsText()
        assertTrue("action=\"/projects\"" in page, "create form should post to /projects")
        assertTrue("href=\"/notes\"" in page, "nav should link to the portal notes path")
        assertFalse(Regex("""(href|action)="/admin""").containsMatchIn(page), "no legacy path should leak into the portal page")
    }

    // ---- legacy /admin URLs ----

    @Test
    fun `legacy admin URLs redirect permanently to the admin host, keeping the query`() = testApplication {
        mount()
        val client = createClient { followRedirects = false }
        listOf(
            "/admin" to "https://$ADMIN/notes",
            "/admin/projects" to "https://$ADMIN/projects",
            "/admin/projects?edit=7" to "https://$ADMIN/projects?edit=7",
        ).forEach { (from, to) ->
            val response = client.get(from) { on("localhost") }
            assertEquals(HttpStatusCode.MovedPermanently, response.status, from)
            assertEquals(to, response.headers[HttpHeaders.Location], from)
        }
    }

    @Test
    fun `legacy redirects need no Access token`() = testApplication {
        mount(withAccess = true)
        val client = createClient { followRedirects = false }
        assertEquals(HttpStatusCode.MovedPermanently, client.get("/admin/projects") { on("localhost") }.status)
    }

    @Test
    fun `legacy form posts and other admin paths are 404`() = testApplication {
        mount()
        val client = createClient { followRedirects = false }
        listOf("/admin", "/admin/projects", "/admin/entries", "/admin/projects/7/delete").forEach { path ->
            val response = client.submitForm(path, parameters { append("name", "x") }) {
                on("localhost"); header(HttpHeaders.Origin, "http://localhost")
            }
            assertEquals(HttpStatusCode.NotFound, response.status, "POST $path")
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/entries") { on("localhost") }.status)
    }

    @Test
    fun `a portal post redirects back into the portal`() = testApplication {
        mount()
        val client = createClient { followRedirects = false }
        val created = client.submitForm(
            "/projects",
            parameters { append("name", "Test"); append("url", "https://example.test") },
        ) { on(ADMIN); header(HttpHeaders.Origin, "http://$ADMIN") }
        assertEquals(HttpStatusCode.Found, created.status)
        assertEquals("/projects", created.headers[HttpHeaders.Location])

        // A blank note skips the now-store call entirely, so this exercises the redirect offline.
        val note = client.submitForm("/notes/entries", parameters { append("body", " ") }) {
            on(ADMIN); header(HttpHeaders.Origin, "http://$ADMIN")
        }
        assertEquals("/notes", note.headers[HttpHeaders.Location])
    }

    @Test
    fun `a browser post from the portal's https origin gets through`() = testApplication {
        // Production shape: the browser sends Origin https://<admin host>, the tunnel delivers plain http.
        // CORS answered an empty 403 for this until the admin host was on its list.
        mount()
        val client = createClient { followRedirects = false }
        val note = client.submitForm("/notes/entries", parameters { append("body", " ") }) {
            on(ADMIN); header(HttpHeaders.Origin, "https://$ADMIN")
        }
        assertEquals(HttpStatusCode.Found, note.status)
        val project = client.submitForm("/projects", parameters { append("name", "T"); append("url", "https://t.test") }) {
            on(ADMIN); header(HttpHeaders.Origin, "https://$ADMIN")
        }
        assertEquals(HttpStatusCode.Found, project.status)
    }

    @Test
    fun `cross-site post to the portal is rejected`() = testApplication {
        mount()
        val response = client.submitForm("/projects", parameters { append("name", "x"); append("url", "https://x.test") }) {
            on(ADMIN); header(HttpHeaders.Origin, "https://evil.test")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin host is no-store everywhere, public notes API is not`() = testApplication {
        mount()
        assertTrue("no-store" in getOn("/projects", ADMIN).headers[HttpHeaders.CacheControl].orEmpty())
        assertTrue("no-store" in getOn("/", ADMIN).headers[HttpHeaders.CacheControl].orEmpty())
        assertFalse("no-store" in getOn("/notes", "localhost").headers[HttpHeaders.CacheControl].orEmpty())
    }

    @Test
    fun `admin host routes stay out of the generated spec`() = testApplication {
        mount()
        assertSpecHidesPortal()
    }

    @Test
    fun `admin host routes stay out of the spec behind the Access layer too`() = testApplication {
        // Production shape: host -> authenticate -> routes. hide() must reach through the extra node.
        mount(withAccess = true)
        assertSpecHidesPortal()
    }

    private suspend fun ApplicationTestBuilder.assertSpecHidesPortal() {
        val spec = Json.parseToJsonElement(getOn("/swagger/documentation.yaml", "localhost").bodyAsText()).jsonObject
        val paths = spec.getValue("paths").jsonObject
        assertFalse("/projects" in paths, "the portal's /projects must be hidden from the spec")
        assertFalse(paths.keys.any { it.startsWith("/admin") }, "the legacy redirects must be hidden from the spec")
        val notesGet = paths.getValue("/notes").jsonObject.getValue("get").jsonObject
        assertEquals("get_notes", notesGet.getValue("operationId").jsonPrimitive.content)
    }

    // ---- shared nav bar ----

    @Test
    fun `nav script lists every host tool and no LAN-only one`() = testApplication {
        mount()
        val response = getOn("/_nav/nav.js", ADMIN)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.JavaScript, response.contentType()?.withoutParameters())
        val js = response.bodyAsText()
        assertTrue("customElements.define('admin-nav'" in js)
        listOf("\"Hub\"" to "\"/\"", "\"Notes\"" to "\"/notes\"", "\"Projects\"" to "\"/projects\"", "\"Embed\"" to "\"/embed/\"")
            .forEach { (label, href) -> assertTrue(label in js && href in js, "nav should carry $label → $href") }
        hubLinks().filter { it.lanOnly }.forEach { assertFalse(it.href in js, "LAN-only ${it.label} must not be in the nav") }
        assertTrue("no-store" in response.headers[HttpHeaders.CacheControl].orEmpty())
    }

    @Test
    fun `nav script is behind Access and absent from the public host`() = testApplication {
        mount(withAccess = true)
        assertEquals(HttpStatusCode.Forbidden, getOn("/_nav/nav.js", ADMIN).status)
        assertEquals(HttpStatusCode.OK, getOn("/_nav/nav.js", ADMIN, token()).status)
        assertEquals(HttpStatusCode.NotFound, getOn("/_nav/nav.js", "localhost").status)
    }

    @Test
    fun `portal pages carry the nav bar`() = testApplication {
        mount()
        listOf("/", "/projects").forEach { path ->
            val page = getOn(path, ADMIN).bodyAsText()
            assertTrue("<admin-nav></admin-nav>" in page && "src=\"/_nav/nav.js\"" in page, "portal $path needs the bar")
        }
    }

    @Test
    fun `nav script stays out of the generated spec`() = testApplication {
        mount(withAccess = true)
        val paths = Json.parseToJsonElement(getOn("/swagger/documentation.yaml", "localhost").bodyAsText())
            .jsonObject.getValue("paths").jsonObject
        assertFalse(paths.keys.any { it.startsWith("/_nav") }, "/_nav must be hidden from the spec")
    }

    // ---- Access token check ----

    @Test
    fun `valid Access token is let through`() = testApplication {
        mount(withAccess = true)
        assertEquals(HttpStatusCode.OK, getOn("/projects", ADMIN, token()).status)
    }

    @Test
    fun `missing, forged, expired, wrong-audience and wrong-issuer tokens are 403`() = testApplication {
        mount(withAccess = true)
        val forger = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val forged = Algorithm.RSA256(forger.public as RSAPublicKey, forger.private as RSAPrivateKey)
        listOf(
            "missing" to null,
            "garbage" to "not-a-jwt",
            "forged" to token(alg = forged),
            "expired" to token(expiresAt = Instant.now().minusSeconds(600)),
            "wrong aud" to token(aud = "some-other-app"),
            "wrong iss" to token(issuer = "https://evil.cloudflareaccess.com"),
        ).forEach { (case, jwt) ->
            assertEquals(HttpStatusCode.Forbidden, getOn("/projects", ADMIN, jwt).status, case)
        }
    }

    @Test
    fun `public routes ignore the Access check`() = testApplication {
        mount(withAccess = true)
        assertEquals(HttpStatusCode.OK, getOn("/health", "localhost").status)
    }

    @Test
    fun `access config needs both team domain and audience`() {
        assertEquals(null, CloudflareAccess.fromEnv(teamDomain = null, audience = "x"))
        assertEquals(null, CloudflareAccess.fromEnv(teamDomain = "team.cloudflareaccess.com", audience = " "))
        assertEquals(ISSUER, CloudflareAccess.issuerFor("https://team.cloudflareaccess.com/"))
        assertEquals(ISSUER, CloudflareAccess.issuerFor("team.cloudflareaccess.com"))
    }

    @Test
    fun `admin host env drops the port`() {
        assertEquals("admin.localhost", adminHostFromEnv("admin.localhost:8080"))
        assertEquals("admin.michaellamb.dev", adminHostFromEnv(" Admin.michaellamb.dev "))
        assertEquals(null, adminHostFromEnv(""))
    }
}
