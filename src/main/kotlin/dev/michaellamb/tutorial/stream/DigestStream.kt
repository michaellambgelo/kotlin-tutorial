/*
 * Live /now digest over Server-Sent Events and WebSockets.
 *
 * Pedagogical focus: **cold Flow**, and what "cold" buys you.
 *
 * `digestFlow` is cold — the `flow { }` block does not run until someone calls `collect`, and it
 * runs again, independently, for every collector. That is why one `val` can serve every connected
 * client without them sharing state: each SSE session gets its own execution of the builder. (A hot
 * `SharedFlow` would be the opposite trade: one execution, many subscribers. The right choice here
 * is cold, because each client should get a value immediately on connect rather than waiting for the
 * next tick.)
 *
 * Structured concurrency does the cleanup: the flow's `while (true)` loop has no exit condition, but
 * when the browser disconnects Ktor cancels the session's coroutine, `delay` throws
 * CancellationException, and the loop unwinds. Nothing here has to notice the disconnect.
 *
 * `distinctUntilChanged()` is the operator that makes this cheap — the digest is rebuilt on a timer,
 * but a client is only woken when the content actually differs from what it last saw.
 */
package dev.michaellamb.tutorial.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import dev.michaellamb.tutorial.widgets.NowDigest
import dev.michaellamb.tutorial.widgets.buildDigest
import io.ktor.client.HttpClient
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.seconds

private val json: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule())

/** How often the digest is rebuilt. Matches WidgetCache's TTL, so most ticks are cache hits. */
private val POLL_INTERVAL = 60.seconds

/**
 * A cold stream of digests: one immediately, then one per [POLL_INTERVAL].
 *
 * Note this is a plain `val` of a `Flow`, not a function returning one — a cold flow is a *recipe*,
 * so the same value can be safely collected by any number of clients.
 */
internal fun digestFlow(client: HttpClient): Flow<NowDigest> = flow {
    while (true) {
        emit(buildDigest(client))
        delay(POLL_INTERVAL) // cancellable: a disconnect unwinds the loop here
    }
}

/** The same stream, rendered to JSON and deduplicated. Shared by both transports below. */
private fun digestJsonFlow(client: HttpClient): Flow<String> =
    digestFlow(client)
        .map { json.writeValueAsString(it) }
        .distinctUntilChanged() // don't wake a client when nothing actually changed

fun Route.digestStreamRoutes(client: HttpClient) {
    /*
     * SSE: one-way, server -> client, over a plain HTTP response that stays open. Reconnection is
     * the browser's job (EventSource retries automatically), which is why this is the right fit for
     * /signage — a TV that must recover on its own after a network blip with nobody in the room.
     */
    sse("/stream/now") {
        digestJsonFlow(client).collect { payload ->
            send(data = payload, event = "digest")
        }
    }

    /*
     * WebSocket: the duplex sibling. Same data, but the client can talk back — so this one answers
     * "refresh" on demand instead of only on the timer. Strictly more capable and strictly more
     * work: no automatic reconnect, and a protocol upgrade some proxies mishandle.
     */
    webSocket("/ws/now") {
        digestJsonFlow(client).collect { payload ->
            send(Frame.Text(payload))
        }
    }
}
