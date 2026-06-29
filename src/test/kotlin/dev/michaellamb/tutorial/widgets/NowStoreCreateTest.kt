package dev.michaellamb.tutorial.widgets

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NowStoreCreateTest {

    private fun client(status: HttpStatusCode, body: String) = HttpClient(
        MockEngine { respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "application/json")) },
    )

    @Test
    fun `create returns the persisted entry parsed from the 201 body`() = runBlocking {
        val json = """{"id":"abc","body":"Shipped it","url":"https://x","createdAt":"2026-06-28T12:00:00Z","expiresAt":1750000000}"""
        val entry = assertNotNull(NowStore.create(client(HttpStatusCode.Created, json), "Shipped it", "https://x", "today"))
        assertEquals("abc", entry.id)
        assertEquals("Shipped it", entry.body)
        assertEquals("https://x", entry.url)
        assertEquals(Instant.parse("2026-06-28T12:00:00Z"), entry.createdAt)
        assertEquals(1750000000L, entry.expiresAt)
    }

    @Test
    fun `create returns null on a non-201 response`() = runBlocking {
        assertNull(NowStore.create(client(HttpStatusCode.BadRequest, """{"error":"body required"}"""), "x", null, "today"))
    }
}
