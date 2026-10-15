/*
 * The hub at admin.michaellamb.dev/ — one page linking every admin tool, each with a live status dot.
 *
 * Status is not probed here. homelab-bot already polls the cluster every 10s, and serves the result
 * joined to its app catalog as `GET /apps.json` (read-only, unauthenticated, LAN-only: node4:8080 has
 * no tunnel route). The hub reads that once per render over the LAN from `HOMELAB_BOT_STATUS_URL`.
 *
 * homelab-bot is therefore a dependency of this page, and the page must not care: a timeout, a
 * non-2xx or a body that won't parse all render every link with "status unknown". Same for an unset
 * URL (local dev). A link whose app homelab-bot doesn't probe shows no dot at all — "not watched"
 * and "down" are different facts.
 *
 * Kotlin note: `withTimeoutOrNull` turns "too slow" into a plain null, so the timeout and the failure
 * paths collapse into one `?:` at the call site instead of two catch blocks.
 */
package dev.michaellamb.tutorial.admin

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

private const val STATUS_TIMEOUT_MS = 1_500L

private val mapper = jacksonObjectMapper()

/** One row of homelab-bot's `/apps.json`. `status`/`checkedAt` are null when the app isn't probed. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AppStatus(val name: String, val status: String? = null, val checkedAt: Long? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AppStatusFeed(val generatedAt: Long = 0, val apps: List<AppStatus> = emptyList())

/**
 * A hub link. [statusName] is the app's `name` in homelab-bot's `admin/AppCatalog.kt` — the join
 * key into `/apps.json`. Rename an app there and its dot here silently disappears, so keep the two
 * in step. Null means homelab-bot doesn't watch it.
 */
data class HubLink(
    val label: String,
    val blurb: String,
    val href: String,
    val lanOnly: Boolean,
    val statusName: String? = null,
)

fun hubLinks(paths: AdminPaths = AdminPaths.PORTAL): List<HubLink> = listOf(
    HubLink("Recently updated", "Publish and delete /now notes", paths.notes, lanOnly = false),
    HubLink("Projects", "Curate the /about Projects list", paths.projects, lanOnly = false),
    HubLink(
        "embed-admin", "discord-embed-builder usage and presence",
        paths.embed, lanOnly = false, statusName = "embed-admin",
    ),
    HubLink(
        "homelab-bot", "Cluster status and ops actions",
        "http://192.168.1.203:8080/admin", lanOnly = true, statusName = "homelab-bot",
    ),
    HubLink(
        "Pi-hole", "LAN DNS filtering",
        "http://192.168.1.126:8089/admin/", lanOnly = true, statusName = "pi-hole",
    ),
    HubLink(
        "Uptime Kuma", "Monitors behind status.michaellamb.dev",
        "http://192.168.1.227:3001", lanOnly = true, statusName = "uptime-kuma",
    ),
)

/** Fetch homelab-bot's feed, or null on any failure (unset URL, timeout, non-2xx, bad body). */
suspend fun fetchAppStatus(client: HttpClient, url: String?): AppStatusFeed? {
    if (url.isNullOrBlank()) return null
    return try {
        withTimeoutOrNull(STATUS_TIMEOUT_MS) {
            val response = client.get(url)
            if (response.status.isSuccess()) mapper.readValue<AppStatusFeed>(response.bodyAsText()) else null
        }
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Exception) {
        null
    }
}

fun Route.adminHubRoutes(
    client: HttpClient,
    statusUrl: String? = System.getenv("HOMELAB_BOT_STATUS_URL"),
    paths: AdminPaths = AdminPaths.PORTAL,
) {
    get("/") {
        val feed = fetchAppStatus(client, statusUrl)
        call.respondText(renderHub(hubLinks(paths), feed), ContentType.Text.Html)
    }
}

/** What a link's dot says. [NONE] = homelab-bot doesn't watch it, so no dot is drawn. */
enum class HubDot(val css: String, val label: String) {
    UP("up", "up"),
    DEGRADED("degraded", "degraded"),
    DOWN("down", "down"),
    UNKNOWN("unknown", "status unknown"),
    NONE("", ""),
}

fun dotFor(link: HubLink, feed: AppStatusFeed?): HubDot {
    val name = link.statusName ?: return HubDot.NONE
    if (feed == null) return HubDot.UNKNOWN
    val app = feed.apps.firstOrNull { it.name == name } ?: return HubDot.UNKNOWN
    return when (app.status) {
        null -> HubDot.NONE
        "OK" -> HubDot.UP
        "DEGRADED" -> HubDot.DEGRADED
        "DOWN" -> HubDot.DOWN
        else -> HubDot.UNKNOWN
    }
}

private fun renderHub(links: List<HubLink>, feed: AppStatusFeed?): String =
    "<!DOCTYPE html>\n" + createHTML().html {
        attributes["lang"] = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title { +"admin" }
            style { unsafe { +HUB_CSS } }
        }
        body {
            div("wrap") {
                h1 { +"admin" }
                p("sub") { +"Every admin tool on the homelab. LAN-only links need the house network or wg0." }
                if (feed == null) {
                    div("banner") { +"Live status unavailable — homelab-bot didn't answer. Links still work." }
                }
                listOf(false to "Here", true to "LAN-only").forEach { (lan, heading) ->
                    val group = links.filter { it.lanOnly == lan }
                    if (group.isEmpty()) return@forEach
                    h2 { +heading }
                    group.forEach { link ->
                        val dot = dotFor(link, feed)
                        a(href = link.href, classes = "card") {
                            div("row") {
                                span("name") { +link.label }
                                if (dot != HubDot.NONE) {
                                    span("dot dot--${dot.css}") { attributes["title"] = dot.label }
                                    span("state") { +dot.label }
                                }
                            }
                            div("blurb") { +link.blurb }
                        }
                    }
                }
            }
        }
    }

private val HUB_CSS = """
:root { color-scheme: light dark; --ok: #2e9e5b; --warn: #c98a12; --bad: #d0453a; --muted: #8a8a8a; }
* { box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Inter", system-ui, sans-serif; margin: 0; padding: 2rem 1rem; line-height: 1.5; }
.wrap { max-width: 640px; margin: 0 auto; }
h1 { font-size: 1.4rem; margin: 0 0 0.25rem; }
h2 { font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.06em; opacity: 0.6; margin: 1.75rem 0 0.5rem; }
.sub { margin: 0 0 1rem; opacity: 0.7; }
.banner { padding: 0.6rem 0.8rem; border-radius: 6px; border: 1px solid var(--warn); margin: 0 0 1rem; font-size: 0.9rem; }
.card { display: block; padding: 0.75rem 0.9rem; margin: 0 0 0.5rem; border: 1px solid rgba(127,127,127,0.3); border-radius: 8px; color: inherit; text-decoration: none; }
.card:hover { border-color: rgba(127,127,127,0.7); }
.row { display: flex; align-items: center; gap: 0.5rem; }
.name { font-weight: 600; flex: 1; }
.blurb { font-size: 0.9rem; opacity: 0.7; }
.dot { width: 0.6rem; height: 0.6rem; border-radius: 50%; background: var(--muted); }
.dot--up { background: var(--ok); } .dot--degraded { background: var(--warn); } .dot--down { background: var(--bad); }
.state { font-size: 0.8rem; opacity: 0.7; }
""".trimIndent()
