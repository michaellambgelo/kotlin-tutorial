/*
 * Homelab cluster status widget (sourced from Uptime Kuma).
 *
 * Pedagogical focus: structured concurrency — `coroutineScope` + `async` +
 * `await()` run two Uptime Kuma calls in parallel (the status-page metadata
 * and the heartbeat snapshot) and combine the results.
 */
package dev.michaellamb.tutorial.widgets

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.html.div
import kotlinx.html.h4
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe
import java.time.Duration

private val kumaBase: String = System.getenv("UPTIME_KUMA_BASE_URL")?.takeIf { it.isNotBlank() } ?: "https://status.michaellamb.dev"
private val kumaSlug: String? = System.getenv("UPTIME_KUMA_STATUS_SLUG")?.takeIf { it.isNotBlank() }
private const val CACHE_KEY = "cluster"
private val TTL: Duration = Duration.ofSeconds(60)
private val mapper = jacksonObjectMapper()

data class ServiceStatus(val name: String, val ok: Boolean, val pingMs: Int?)
data class ServiceGroup(val name: String, val services: List<ServiceStatus>)

fun Route.clusterWidget(client: HttpClient, cache: WidgetCache) {
    get("/cluster") {
        if (kumaSlug == null) {
            call.respondText(unconfiguredFragment(), ContentType.Text.Html)
            return@get
        }
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            renderCluster(fetchKumaStatus(client))
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

private suspend fun fetchKumaStatus(client: HttpClient): List<ServiceGroup> = coroutineScope {
    val pageDeferred = async {
        mapper.readTree(client.get("$kumaBase/api/status-page/$kumaSlug").bodyAsText())
    }
    val heartbeatDeferred = async {
        mapper.readTree(client.get("$kumaBase/api/status-page/heartbeat/$kumaSlug").bodyAsText())
    }
    val page = pageDeferred.await()
    val heartbeats = heartbeatDeferred.await().path("heartbeatList")

    page.path("publicGroupList").map { group ->
        val services = group.path("monitorList").map { monitor ->
            val id = monitor.path("id").asText()
            val latest = heartbeats.path(id).lastOrNull()
            ServiceStatus(
                name = monitor.path("name").asText(),
                ok = latest?.path("status")?.asInt() == 1,
                pingMs = latest?.path("ping")?.takeIf { !it.isNull }?.asInt(),
            )
        }
        ServiceGroup(group.path("name").asText(), services)
    }
}

private fun JsonNode.lastOrNull(): JsonNode? = if (isArray && size() > 0) get(size() - 1) else null

private fun renderCluster(groups: List<ServiceGroup>): String = createHTML().div("widget cluster-widget") {
    style { unsafe { +CLUSTER_CSS } }
    groups.forEach { group ->
        h4("group-name") { +group.name }
        table {
            thead {
                tr {
                    th { +"Service" }
                    th(classes = "col-status") { +"Status" }
                    th(classes = "col-ping") { +"Ping" }
                }
            }
            tbody {
                group.services.forEach { s ->
                    tr {
                        td("name") { +s.name }
                        td("status ${if (s.ok) "ok" else "down"}") {
                            span("dot") { +(if (s.ok) "●" else "○") }
                            +(if (s.ok) " Up" else " Down")
                        }
                        td("ping") { +(s.pingMs?.let { "${it}ms" } ?: "—") }
                    }
                }
            }
        }
    }
}

private fun unconfiguredFragment(): String = createHTML().div("widget cluster-widget unconfigured") {
    style { unsafe { +CLUSTER_CSS } }
    span { +"Cluster widget not configured (set UPTIME_KUMA_STATUS_SLUG)." }
}

private val CLUSTER_CSS = """
.cluster-widget { font-family: inherit; }
.cluster-widget .group-name { margin: 1.25rem 0 0.5rem; font-size: 1rem; opacity: 0.6; text-transform: uppercase; letter-spacing: 0.05em; }
.cluster-widget table { width: 100%; border-collapse: collapse; }
.cluster-widget th { text-align: left; padding: 0.5rem 0.75rem; font-weight: 500; font-size: 0.85rem; opacity: 0.5; border-bottom: 1px solid rgba(255,255,255,0.15); }
.cluster-widget td { padding: 0.6rem 0.75rem; border-bottom: 1px solid rgba(255,255,255,0.08); }
.cluster-widget tr:last-child td { border-bottom: none; }
.cluster-widget .col-status, .cluster-widget .col-ping { width: 7rem; }
.cluster-widget .status .dot { margin-right: 0.35rem; }
.cluster-widget .status.ok { color: #5fb573; }
.cluster-widget .status.down { color: #e57373; }
.cluster-widget .ping { font-variant-numeric: tabular-nums; opacity: 0.75; }
.cluster-widget .name { font-weight: 500; }
""".trimIndent()
