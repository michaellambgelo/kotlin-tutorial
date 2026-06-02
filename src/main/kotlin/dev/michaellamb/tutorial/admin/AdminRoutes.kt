/*
 * Admin form for the "Recently updated by Michael" feed, served by Ktor at
 * /admin and used to create/delete entries in the now-store Worker.
 *
 * Access control is enforced at the EDGE by Cloudflare Access: a self-hosted
 * Access application scoped to `kotlin-tutorial.michaellamb.dev/admin*` with a
 * One-time-PIN email policy. Only an authenticated browser reaches these
 * handlers in production (locally there is no Access, so /admin is open for
 * dev). The write/delete calls are proxied to the now-store Worker via NowStore,
 * which presents the Access service-token headers — the browser never holds a
 * credential.
 *
 * Plain same-origin HTML form POSTs: no JS, no JSON, no CORS preflight.
 */
package dev.michaellamb.tutorial.admin

import dev.michaellamb.tutorial.widgets.NowEntry
import dev.michaellamb.tutorial.widgets.NowStore
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.input
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.unsafe
import java.time.Instant

private val VALID_EXPIRY = setOf("today", "day", "week")

fun Route.adminRoutes(client: HttpClient) {
    route("/admin") {
        get {
            val result = runCatching { NowStore.list(client) }
            call.respondText(
                renderAdminPage(result.getOrDefault(emptyList()), result.isFailure),
                ContentType.Text.Html,
            )
        }

        post("/entries") {
            val params = call.receiveParameters()
            val body = params["body"]?.trim().orEmpty()
            val url = params["url"]?.trim()?.takeIf { it.isNotBlank() }
            val expiry = params["expiry"]?.takeIf { it in VALID_EXPIRY } ?: "today"
            if (body.isNotBlank()) {
                runCatching { NowStore.create(client, body, url, expiry) }
            }
            call.respondRedirect("/admin")
        }

        post("/entries/{id}/delete") {
            call.parameters["id"]?.takeIf { it.isNotBlank() }?.let { id ->
                runCatching { NowStore.delete(client, id) }
            }
            call.respondRedirect("/admin")
        }
    }
}

private fun relativeUntil(epochSeconds: Long): String {
    val secs = epochSeconds - Instant.now().epochSecond
    return when {
        secs <= 0 -> "now"
        secs < 3600 -> "in ${secs / 60}m"
        secs < 86400 -> "in ${secs / 3600}h"
        else -> "in ${secs / 86400}d"
    }
}

private fun renderAdminPage(entries: List<NowEntry>, loadFailed: Boolean): String =
    "<!DOCTYPE html>\n" + createHTML().html {
        attributes["lang"] = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title { +"now · admin" }
            style { unsafe { +ADMIN_CSS } }
        }
        body {
            div("wrap") {
                h1 { +"Recently updated — admin" }
                p("sub") { +"Publish a short blurb to the /now page. Entries expire automatically." }

                if (loadFailed) {
                    div("banner") {
                        +"Couldn't load current entries from the store — check NOW_STORE_URL and the Access service-token credentials."
                    }
                }

                form(action = "/admin/entries", method = FormMethod.post, classes = "publish") {
                    textArea {
                        name = "body"
                        attributes["required"] = "required"
                        attributes["rows"] = "3"
                        attributes["maxlength"] = "600"
                        attributes["placeholder"] = "What did you ship or update?"
                    }
                    input(type = InputType.url, name = "url") {
                        attributes["placeholder"] = "https://… (optional link)"
                    }
                    div("row") {
                        select {
                            name = "expiry"
                            option {
                                value = "today"
                                selected = true
                                +"Tonight (midnight Central)"
                            }
                            option {
                                value = "day"
                                +"+1 day"
                            }
                            option {
                                value = "week"
                                +"+1 week"
                            }
                        }
                        button(type = ButtonType.submit) { +"Publish" }
                    }
                }

                h2 { +"Current entries" }
                if (entries.isEmpty()) {
                    p("empty") { +"No entries right now." }
                } else {
                    entries.forEach { e ->
                        div("entry") {
                            div("body") { +e.body }
                            div("meta") {
                                if (!e.url.isNullOrBlank()) {
                                    a(href = e.url, target = "_blank") {
                                        attributes["rel"] = "noopener"
                                        +e.url
                                    }
                                }
                                span("time") { +"expires ${relativeUntil(e.expiresAt)}" }
                            }
                            form(action = "/admin/entries/${e.id}/delete", method = FormMethod.post, classes = "del") {
                                button(type = ButtonType.submit, classes = "danger") {
                                    +"Delete"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private val ADMIN_CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Inter", system-ui, sans-serif; margin: 0; padding: 2rem 1rem; line-height: 1.5; }
.wrap { max-width: 640px; margin: 0 auto; }
h1 { font-size: 1.4rem; margin: 0 0 0.25rem; }
h2 { font-size: 1.05rem; margin: 2rem 0 0.5rem; }
.sub { opacity: 0.6; margin: 0 0 1.5rem; font-size: 0.9rem; }
.banner { background: rgba(220,80,80,0.12); border: 1px solid rgba(220,80,80,0.4); border-radius: 8px; padding: 0.7rem 0.9rem; font-size: 0.85rem; margin-bottom: 1.25rem; }
.publish { display: flex; flex-direction: column; gap: 0.6rem; }
.publish textarea, .publish input { width: 100%; font: inherit; padding: 0.6rem 0.7rem; border-radius: 8px; border: 1px solid rgba(127,127,127,0.4); background: transparent; color: inherit; }
.publish textarea { resize: vertical; }
.publish .row { display: flex; gap: 0.6rem; align-items: center; }
.publish select { flex: 1; font: inherit; padding: 0.55rem 0.6rem; border-radius: 8px; border: 1px solid rgba(127,127,127,0.4); background: transparent; color: inherit; }
button { font: inherit; padding: 0.55rem 1.1rem; border-radius: 999px; border: none; background: #1d6fe0; color: #fff; cursor: pointer; }
button:hover { background: #1860c4; }
button.danger { background: transparent; color: #d65050; border: 1px solid rgba(214,80,80,0.5); padding: 0.3rem 0.8rem; font-size: 0.8rem; }
button.danger:hover { background: rgba(214,80,80,0.12); }
.entry { padding: 0.8rem 0; border-bottom: 1px solid rgba(127,127,127,0.18); }
.entry:last-child { border-bottom: none; }
.entry .body { white-space: pre-wrap; }
.entry .meta { margin: 0.3rem 0 0.5rem; font-size: 0.8rem; opacity: 0.6; display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: center; }
.entry .meta a { color: inherit; }
.empty { opacity: 0.6; font-style: italic; }
""".trimIndent()
