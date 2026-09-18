/*
 * A self-contained htmx demo at GET /htmx.
 *
 * Why this is a separate page rather than a rewrite of the home page: the home page's `Run ▶`
 * buttons already work, via hand-written fetch() in HomeRoutes.PAGE_JS. Replacing working,
 * readable JS with a second paradigm would trade a real thing for a demonstration. So this page
 * shows the same idea — click a control, replace part of the DOM — with the JS deleted, and leaves
 * the comparison to the reader.
 *
 * Pedagogical focus: **typed attributes over stringly-typed ones, again**. Ktor's htmx integration
 * gives kotlinx.html an `hx { }` block whose properties are `var`s backed by property delegates
 * (see /tour/property-delegates — `public var get: String? by HxAttributeKeys.Get`). So a typo in
 * `hx-swap` is a compile error rather than an attribute the browser silently ignores, and the
 * allowed swap values are constants rather than strings.
 *
 * The server side is just routes that return HTML fragments. `request.isHtmx` distinguishes an
 * htmx-issued request from a full page load — the same handler can serve both.
 */
package dev.michaellamb.tutorial.htmx

import dev.michaellamb.tutorial.catalog.tourChapters
import io.ktor.htmx.HxSwap
import io.ktor.htmx.html.hx
import io.ktor.http.ContentType
import io.ktor.server.htmx.isHtmx
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

@OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)
fun Route.htmxRoutes() {
    get("/htmx") {
        call.respondText(renderHtmxPage(), ContentType.Text.Html)
    }

    // The fragment endpoints. Each returns a bare <div>, not a document — htmx splices the response
    // into the target element, so anything more would be discarded.
    get("/htmx/chapter/{name}") {
        val name = call.parameters["name"].orEmpty()
        val routes = tourChapters[name].orEmpty()
        respondFragment {
            div("fragment") {
                h2 { +"$name — ${routes.size} routes" }
                // Proves the header round-trip: this is only "true" when htmx made the request.
                p("meta") { +"served to an htmx request: ${call.request.isHtmx}" }
                ul {
                    routes.forEach { path ->
                        li { a(href = path) { code { +path } } }
                    }
                }
            }
        }
    }
}

private suspend fun RoutingContext.respondFragment(block: FlowContent.() -> Unit) {
    call.respondText(
        createHTML().div { block() },
        ContentType.Text.Html,
    )
}

@OptIn(io.ktor.utils.io.ExperimentalKtorApi::class)
private fun renderHtmxPage(): String {
    val body = createHTML().html {
        lang = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("kotlin-tutorial — htmx")
            script(src = "https://unpkg.com/htmx.org@2.0.4") {}
            style { unsafe { +CSS } }
        }
        body {
            h1 { +"htmx" }
            p {
                +"The same interaction as the home page's "
                code { +"Run ▶" }
                +" buttons, with no hand-written JavaScript. Each button declares where to GET from "
                +"and which element to replace; htmx does the rest."
            }

            div("row") {
                tourChapters.keys.forEach { chapter ->
                    button {
                        // Typed hx-* attributes: these are Kotlin properties, so a typo fails to
                        // compile and HxSwap's values are constants rather than magic strings.
                        // `hx` extends the tag's attribute map, not the tag itself.
                        attributes.hx {
                            get = "/htmx/chapter/$chapter"
                            target = "#panel"
                            // NOT HxSwap.innerHtml. That constant is "innerHtml", which htmx does
                            // not recognise — verified in-browser: it behaves identically to a
                            // garbage value, because htmx's fallback *is* innerHTML, so the bug is
                            // invisible unless you compare against a swap that should differ.
                            // HxSwap.outerHtml is correctly "outerHTML", so this is a typo in that
                            // one constant (Ktor 3.5.0). Worth an upstream issue.
                            swap = "innerHTML"
                            indicator = "#spinner"
                        }
                        +chapter
                    }
                }
            }

            div("spinner") {
                id = "spinner"
                +"loading…"
            }

            div("panel") {
                id = "panel"
                p("meta") { +"pick a chapter above" }
            }

            div("row") {
                button {
                    attributes.hx {
                        get = "/htmx/chapter/beginner"
                        target = "#panel"
                        // A non-default swap, and one whose Ktor constant is correct: this appends
                        // rather than replacing, which is how you can tell a swap value was actually
                        // honoured instead of quietly falling back.
                        swap = HxSwap.beforeEnd
                    }
                    +"append beginner (hx-swap=beforeend)"
                }
            }

            p("footnote") {
                +"Server side these are plain routes returning HTML fragments — see htmx/HtmxDemo.kt. "
                a(href = "/") { +"← back to the directory" }
            }
        }
    }
    return "<!DOCTYPE html>\n$body"
}

private val CSS = """
body { background:#1e1f22; color:#bcbec4; font:14px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace; margin:0; padding:32px; }
h1 { color:#e6e6e6; font-size:20px; }
h2 { color:#e6e6e6; font-size:15px; margin:0 0 8px; }
a { color:#6aa3f0; }
code { color:#c9e5ff; }
.row { display:flex; gap:8px; flex-wrap:wrap; margin:20px 0 12px; }
button { font:inherit; padding:6px 14px; border-radius:4px; cursor:pointer; border:1px solid #3a3f44; background:#2b2d30; color:#bcbec4; }
button:hover { border-color:#4c8df6; color:#fff; }
.panel { background:#2b2d30; border:1px solid #3a3f44; border-radius:6px; padding:16px; min-height:80px; }
.meta { color:#7a7f87; font-size:12px; }
.footnote { color:#7a7f87; font-size:12px; margin-top:24px; }
ul { margin:0; padding-left:18px; }
/* htmx toggles this class on the indicator for the duration of the request. */
.spinner { color:#7a7f87; font-size:12px; height:16px; visibility:hidden; }
.spinner.htmx-request { visibility:visible; }
""".trimIndent()
