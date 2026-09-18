/*
 * Server-Sent Events and WebSockets.
 *
 * Pedagogical focus: both plugins do the same job — hold a connection open and hand the handler a
 * suspending session — but they pick different points on the capability/cost curve. See
 * stream/DigestStream.kt for the two handlers side by side.
 */
package dev.michaellamb.tutorial.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.resources.Resources
import io.ktor.server.sse.SSE
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlin.time.Duration.Companion.seconds

fun Application.configureStreaming() {
    // Required before any `get<SomeResource> { }` route — it registers the URL <-> class codec.
    install(Resources)
    install(SSE)
    install(WebSockets) {
        // Without a ping an idle socket dies silently behind Cloudflare Tunnel.
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }
}
