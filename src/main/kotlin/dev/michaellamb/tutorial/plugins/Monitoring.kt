/*
 * Observability: request logging, request correlation, and Prometheus metrics.
 *
 * Pedagogical focus: **why a ThreadLocal is the wrong tool in a coroutine**. SLF4J's MDC is
 * ThreadLocal-backed, and a suspending handler can resume on a different thread than it started on,
 * so a plain `MDC.put` does not survive the first suspension point. `callIdMdc` exists because Ktor
 * has to bridge the call's own context into the MDC at each resumption — the same problem
 * `CoroutineContext` elements solve in /tour/coroutines, seen from the framework's side.
 */
package dev.michaellamb.tutorial.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.slf4j.event.Level
import java.util.UUID

/**
 * Shared with `Routing.kt`, which exposes it at `GET /metrics`.
 *
 * `by lazy` (see /tour/properties): the registry is built once, on first touch, and every later read
 * gets the same instance — without the nullable `var` + null-check a hand-rolled singleton needs.
 */
val meterRegistry: PrometheusMeterRegistry by lazy {
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

fun Application.configureMonitoring() {
    install(CallId) {
        // Trust an inbound correlation id so a request can be followed across services — Cloudflare
        // Tunnel sits in front of this app, and homelab-bot calls it directly.
        retrieveFromHeader("X-Request-Id")
        generate { UUID.randomUUID().toString() }
        // Only accept ids that can't corrupt a log line.
        verify { it.isNotBlank() && it.length <= 64 && it.none { ch -> ch.isISOControl() } }
        replyToHeader("X-Request-Id")
    }

    install(CallLogging) {
        level = Level.INFO
        // Bridges the call id into the MDC across suspension points; `%X{callId}` in logback.xml
        // then prints it on every line the handler logs.
        callIdMdc("callId")
    }

    install(MicrometerMetrics) {
        registry = meterRegistry
    }
}
