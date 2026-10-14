package dev.michaellamb.tutorial.admin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdminHubTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientAnswering(body: String) = HttpClient(MockEngine { respond(body, HttpStatusCode.OK, json) })

    private val feedBody = """
        {"generatedAt": 5, "apps": [
          {"name": "homelab-bot", "url": "x", "reach": "PRIVATE", "nodeId": 4, "status": "OK", "checkedAt": 5},
          {"name": "embed-admin", "url": "x", "reach": "PUBLIC", "nodeId": 2, "status": "DOWN", "checkedAt": 5},
          {"name": "uptime-kuma", "url": "x", "reach": "PRIVATE", "nodeId": 3, "status": "DEGRADED", "checkedAt": 5},
          {"name": "pi-hole", "url": "x", "reach": "PRIVATE", "nodeId": 6, "status": null, "checkedAt": null}
        ]}
    """.trimIndent()

    private fun link(name: String) = hubLinks().single { it.statusName == name }

    @Test
    fun `feed parses and maps each status to a dot`() = runBlocking {
        val feed = fetchAppStatus(clientAnswering(feedBody), "http://bot/apps.json")
        assertEquals(HubDot.UP, dotFor(link("homelab-bot"), feed))
        assertEquals(HubDot.DOWN, dotFor(link("embed-admin"), feed))
        assertEquals(HubDot.DEGRADED, dotFor(link("uptime-kuma"), feed))
    }

    @Test
    fun `an app homelab-bot does not probe gets no dot, not a down dot`() = runBlocking {
        val feed = fetchAppStatus(clientAnswering(feedBody), "http://bot/apps.json")
        assertEquals(HubDot.NONE, dotFor(link("pi-hole"), feed))
    }

    @Test
    fun `local links never carry a dot`() {
        hubLinks().filter { it.statusName == null }.forEach { assertEquals(HubDot.NONE, dotFor(it, null)) }
    }

    @Test
    fun `every failure mode degrades to status unknown`() = runBlocking {
        val failing = listOf(
            "unset" to (HttpClient(MockEngine { error("must not be called") }) to null),
            "non-2xx" to (HttpClient(MockEngine { respondError(HttpStatusCode.BadGateway) }) to "http://bot/apps.json"),
            "bad body" to (clientAnswering("<html>not json</html>") to "http://bot/apps.json"),
            "throws" to (HttpClient(MockEngine { error("connection refused") }) to "http://bot/apps.json"),
            "slow" to (HttpClient(MockEngine { delay(5_000); respond(feedBody, HttpStatusCode.OK, json) }) to "http://bot/apps.json"),
        )
        failing.forEach { (case, setup) ->
            val (client, url) = setup
            val feed = fetchAppStatus(client, url)
            assertNull(feed, case)
            assertEquals(HubDot.UNKNOWN, dotFor(link("homelab-bot"), feed), case)
        }
    }

    @Test
    fun `an app missing from the feed is unknown`() = runBlocking {
        val feed = fetchAppStatus(clientAnswering("""{"generatedAt": 1, "apps": []}"""), "http://bot/apps.json")
        assertEquals(HubDot.UNKNOWN, dotFor(link("homelab-bot"), feed))
    }
}
