/*
 * "Recently updated by Michael" widget — a hand-curated feed (distinct from the
 * auto-derived Letterboxd/Steam/GitHub widgets above it on /now).
 *
 * Pedagogical focus: an `object` declaration as a stateless service client, and
 * the Ktor HTTP *client* issuing write requests (POST/DELETE) with header-based
 * auth — the other widgets only ever GET.
 *
 * Data lives in a Cloudflare Worker + KV (`now-store.michaellamb.dev`), which is
 * gated by a Cloudflare Access service-token policy. This service is the only
 * caller and presents the CF-Access-Client-Id / CF-Access-Client-Secret headers
 * (NowStore.accessHeaders). The admin form (see admin/AdminRoutes.kt) reuses
 * NowStore to create/delete entries.
 */
package dev.michaellamb.tutorial.widgets

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.unsafe
import java.time.Duration
import java.time.Instant

private const val CACHE_KEY = "recently-updated"
private val TTL: Duration = Duration.ofSeconds(60)

// Shared Jackson mapper for now-store JSON: `findAndRegisterModules()` pulls in JavaTimeModule, and
// disabling WRITE_DATES_AS_TIMESTAMPS makes `Instant` fields serialize as ISO-8601 (matching the
// now-store shape and what JSON consumers parse) rather than a numeric epoch. Used by NowStore
// (deserialize) and the /now digest endpoint (serialize — see NowDigest.kt).
internal val nowStoreMapper = jacksonObjectMapper().findAndRegisterModules()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NowEntry(
    val id: String,
    val body: String,
    val url: String? = null,
    val createdAt: Instant,
    val expiresAt: Long,
)

/**
 * Stateless client for the now-store Worker. Stays a no-op-safe object: when the
 * Access credentials are unset (local dev against `wrangler dev`, which has no
 * Access in front of it) the headers are simply omitted.
 */
object NowStore {
    val baseUrl: String =
        System.getenv("NOW_STORE_URL")?.takeIf { it.isNotBlank() } ?: "https://now-store.michaellamb.dev"
    private val clientId: String? = System.getenv("CF_ACCESS_CLIENT_ID")?.takeIf { it.isNotBlank() }
    private val clientSecret: String? = System.getenv("CF_ACCESS_CLIENT_SECRET")?.takeIf { it.isNotBlank() }

    private fun HttpRequestBuilder.accessHeaders() {
        if (clientId != null && clientSecret != null) {
            header("CF-Access-Client-Id", clientId)
            header("CF-Access-Client-Secret", clientSecret)
        }
    }

    suspend fun list(client: HttpClient): List<NowEntry> {
        val body = client.get("$baseUrl/entries") { accessHeaders() }.bodyAsText()
        return nowStoreMapper.readValue(body)
    }

    suspend fun create(client: HttpClient, body: String, url: String?, expiry: String) {
        val payload = buildMap {
            put("body", body)
            put("expiry", expiry)
            if (!url.isNullOrBlank()) put("url", url)
        }
        client.post("$baseUrl/entries") {
            accessHeaders()
            contentType(ContentType.Application.Json)
            setBody(nowStoreMapper.writeValueAsString(payload))
        }
    }

    suspend fun delete(client: HttpClient, id: String) {
        client.delete("$baseUrl/entries/$id") { accessHeaders() }
    }
}

fun Route.recentlyUpdatedWidget(client: HttpClient, cache: WidgetCache) {
    get("/recently-updated") {
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            runCatching { renderRecentlyUpdated(NowStore.list(client)) }
                .getOrElse { unavailableFragment() }
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

private fun relativeTime(from: Instant): String {
    val secs = Duration.between(from, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        else -> "${secs / 86400}d ago"
    }
}

private fun expiresIn(epochSeconds: Long): String {
    val secs = epochSeconds - Instant.now().epochSecond
    return when {
        secs <= 0 -> "expiring"
        secs < 3600 -> "expires in ${secs / 60}m"
        secs < 86400 -> "expires in ${secs / 3600}h"
        else -> "expires in ${secs / 86400}d"
    }
}

private fun renderRecentlyUpdated(entries: List<NowEntry>): String =
    createHTML().div("widget recently-updated-widget") {
        style { unsafe { +RECENTLY_UPDATED_CSS } }
        if (entries.isEmpty()) {
            span("empty") { +"Nothing new right now." }
            return@div
        }
        entries.forEach { e ->
            div("entry") {
                div("body") { +e.body }
                div("meta") {
                    if (!e.url.isNullOrBlank()) {
                        a(href = e.url, target = "_blank") {
                            attributes["rel"] = "noopener"
                            +"view ↗"
                        }
                    }
                    span("time") { +relativeTime(e.createdAt) }
                    span("expires") { +expiresIn(e.expiresAt) }
                }
            }
        }
    }

private fun unavailableFragment(): String =
    createHTML().div("widget recently-updated-widget") {
        style { unsafe { +RECENTLY_UPDATED_CSS } }
        span("empty") { +"Updates unavailable right now." }
    }

private val RECENTLY_UPDATED_CSS = """
.recently-updated-widget { font-family: inherit; }
.recently-updated-widget .entry { padding: 0.7rem 0; border-bottom: 1px solid rgba(127,127,127,0.18); }
.recently-updated-widget .entry:last-child { border-bottom: none; }
.recently-updated-widget .body { font-size: 0.95rem; line-height: 1.5; white-space: pre-wrap; }
.recently-updated-widget .meta { margin-top: 0.3rem; font-size: 0.8rem; opacity: 0.6; display: flex; gap: 0.75rem; align-items: center; }
.recently-updated-widget .meta a { color: inherit; text-decoration: none; }
.recently-updated-widget .meta a:hover { text-decoration: underline; }
.recently-updated-widget .time { font-variant-numeric: tabular-nums; }
.recently-updated-widget .expires { font-variant-numeric: tabular-nums; font-style: italic; opacity: 0.8; }
.recently-updated-widget .expires::before { content: "·"; margin-right: 0.75rem; opacity: 0.6; font-style: normal; }
.recently-updated-widget .empty { opacity: 0.7; font-style: italic; }
""".trimIndent()
