package dev.michaellamb.tutorial.admin

import dev.michaellamb.tutorial.widgets.NowEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NoteHookTest {

    @Test
    fun `announce posts a bearer-authed JSON payload to the target`() = runBlocking {
        val captured = AtomicReference<HttpRequestData?>(null)
        val engine = MockEngine { req ->
            captured.set(req)
            respond(ByteReadChannel(""), HttpStatusCode.Accepted)
        }
        val entry = NowEntry(
            id = "abc",
            body = "Shipped it",
            url = "https://blog.example/x",
            createdAt = Instant.parse("2026-06-28T12:00:00Z"),
            expiresAt = 1750000000,
        )

        NoteHook.announce(HttpClient(engine), entry, "http://node4.lan:8080/hooks/now-entry", "test-secret")

        val req = assertNotNull(captured.get())
        assertEquals("http://node4.lan:8080/hooks/now-entry", req.url.toString())
        assertEquals("Bearer test-secret", req.headers[HttpHeaders.Authorization])
        val text = (req.body as TextContent).text
        assertTrue(text.contains("\"abc\""), text)
        assertTrue(text.contains("Shipped it"), text)
        assertTrue(text.contains("https://blog.example/x"), text)
        assertTrue(text.contains("2026-06-28T12:00:00Z"), text)
    }

    @Test
    fun `retract DELETEs the entry-scoped resource with a bearer-authed request`() = runBlocking {
        val captured = AtomicReference<HttpRequestData?>(null)
        val engine = MockEngine { req ->
            captured.set(req)
            respond(ByteReadChannel(""), HttpStatusCode.NoContent)
        }

        NoteHook.retract(HttpClient(engine), "abc", "http://node4.lan:8080/hooks/now-entry", "test-secret")

        val req = assertNotNull(captured.get())
        assertEquals("http://node4.lan:8080/hooks/now-entry/abc", req.url.toString())
        assertEquals("Bearer test-secret", req.headers[HttpHeaders.Authorization])
        assertEquals(io.ktor.http.HttpMethod.Delete, req.method)
    }
}
