/*
 * Home page (GET /).
 *
 * Pedagogical focus: kotlinx.html DSL composing a whole HTML document — head, body,
 * inline <style>, inline <script> — not just a fragment. The page itself is a directory
 * of every route the service exposes, with a "Run" button per endpoint that calls the
 * live API via fetch() (same origin, no CORS hop). The IDE/Darcula palette nods at the
 * fact that this is a Kotlin teaching service.
 */
package dev.michaellamb.tutorial.home

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.code
import kotlinx.html.details
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.summary
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe

private data class EndpointCard(
    val method: String,
    val path: String,
    val summary: String,
    val snippet: String,
    val requestBody: String? = null,
)

private val tourEndpoints: List<EndpointCard> = listOf(
    EndpointCard(
        method = "GET",
        path = "/tour/data-class",
        summary = "data class — auto-derived equals, copy, destructuring, componentN.",
        snippet = """
            data class User(val id: Int, val name: String, val email: String)

            val original = User(1, "Han Solo", "han@falcon.test")
            val renamed = original.copy(name = "Chewbacca")
            val (_, name, email) = renamed
        """.trimIndent(),
    ),
    EndpointCard(
        method = "POST",
        path = "/tour/null-safety",
        summary = "Nullable types: safe call ?., Elvis ?:, let, smart cast.",
        snippet = """
            val length = nickname?.length            // safe call
            val display = nickname ?: "anonymous"    // Elvis fallback
            val shouted = nickname?.let { it.uppercase() }
            if (nickname != null) nickname[0]        // smart cast inside if
        """.trimIndent(),
        requestBody = """{ "nickname": "luke" }""",
    ),
    EndpointCard(
        method = "POST",
        path = "/tour/sealed-when",
        summary = "sealed interface + exhaustive when — no else needed.",
        snippet = """
            sealed interface Shape {
              data class Circle(val radius: Double) : Shape
              data class Square(val side: Double) : Shape
            }

            fun Shape.area(): Double = when (this) {
              is Shape.Circle -> PI * radius * radius
              is Shape.Square -> side * side
            }
        """.trimIndent(),
        requestBody = """{ "type": "Circle", "radius": 5 }""",
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/coroutines",
        summary = "Structured concurrency — coroutineScope + async parallelism.",
        snippet = """
            val parallelMs = measureTimeMillis {
              coroutineScope {
                val a = async { fakeApiCall("a", 200) }
                val b = async { fakeApiCall("b", 200) }
                listOf(a.await(), b.await())
              }
            }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/extensions?text=Hello%20World&n=17",
        summary = "Extension functions — add methods to types you don't own.",
        snippet = """
            fun String.toSlug(): String =
              lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .trim()
                .replace(Regex("\\s+"), "-")

            fun Int.isPrime(): Boolean { /* ... */ }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/scope-functions",
        summary = "let, run, with, apply, also — same effect, five flavors.",
        snippet = """
            "kotlin".let { name -> "Hello, ${'$'}name!" }      // it, returns block
            "kotlin".run { "Hello, ${'$'}this!" }              // this, returns block
            GreetingBuilder().apply { name = "kotlin" }    // this, returns receiver
            "kotlin".also { println("logging: ${'$'}it") }     // it, returns receiver
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/collections",
        summary = "Collection operations — groupBy, sumOf, partition, runningFold.",
        snippet = """
            val byAuthor = library.groupBy { it.author }
            val totalPages = library.sumOf { it.pages }
            val (long, short) = library.partition { it.pages > 500 }
            library.runningFold(0) { acc, b -> acc + b.pages }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/generics",
        summary = "Generics — bounded type params, out variance, reified types.",
        snippet = """
            fun <T : Comparable<T>> maxOf(items: List<T>): T? = items.maxOrNull()

            class Box<out T>(val value: T)               // covariant producer

            inline fun <reified T> List<*>.ofType() = filterIsInstance<T>()
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/higher-order-functions?x=10",
        summary = "Higher-order functions — functions as values, refs, composition.",
        snippet = """
            val double: (Int) -> Int = { it * 2 }

            infix fun <A, B, C> ((A) -> B).then(g: (B) -> C): (A) -> C =
              { a -> g(this(a)) }

            listOf("luke", "leia").map(String::uppercase)  // function reference
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/interfaces",
        summary = "Interfaces + polymorphism — default methods, dynamic dispatch.",
        snippet = """
            interface Animal {
              val name: String
              fun sound(): String
              fun describe() = "${'$'}name says ${'$'}{sound()}"   // default method
            }

            class Dog(override val name: String) : Animal {
              override fun sound() = "Woof"
            }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/sequences",
        summary = "Sequences — lazy evaluation, short-circuit, generateSequence.",
        snippet = """
            (1..1000).asSequence()
              .map { it * it }        // lazy — runs per element
              .first { it > 1000 }    // short-circuits (~32 invocations)

            generateSequence(0 to 1) { (a, b) -> b to (a + b) }
              .map { it.first }.take(10).toList()
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/result?n=16",
        summary = "Error handling — runCatching, Result, fold, getOrElse.",
        snippet = """
            class NegativeInputException(n: Int) :
              IllegalArgumentException("negative: ${'$'}n")

            val folded = runCatching { checkedSqrt(n) }
              .fold({ "ok: ${'$'}it" }, { "error: ${'$'}{it.message}" })

            runCatching { checkedSqrt(-1) }.getOrElse { Double.NaN }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/reflection",
        summary = "Reflection — KClass, memberProperties, callable references.",
        snippet = """
            data class Droid(val id: String, val model: String)

            val props = Droid::class.memberProperties
              .associate { it.name to it.get(r2) }

            Droid::class.isData          // true
            Droid::model.get(r2)         // callable reference
        """.trimIndent(),
    ),
)

private val widgetCards: List<Triple<String, String, String>> = listOf(
    Triple("/widgets/letterboxd", "Letterboxd", "Recent watches via RSS — kotlinx.html DSL fragment, 60s cache."),
    Triple("/widgets/steam", "Steam", "Recently played games via Steam Web API — graceful fallback if STEAM_API_KEY unset."),
    Triple("/widgets/cluster", "Cluster", "Homelab status via Uptime Kuma — parallel async fetches with structured concurrency."),
)

fun Route.homeRoutes() {
    get("/") {
        call.respondText(renderHome(), ContentType.Text.Html)
    }
}

private fun renderHome(): String {
    val body = createHTML().html {
        lang = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title("kotlin-tutorial — explore the API")
            meta(name = "description", content = "A pedagogical Ktor service. Each route teaches one Kotlin language feature.")
            link(rel = "icon", href = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>K</text></svg>")
            style { unsafe { +PAGE_CSS } }
        }
        body {
            header("page-header") {
                div("title-row") {
                    h1 { +"kotlin-tutorial" }
                    span("health-badge") {
                        id = "health-badge"
                        +"checking…"
                    }
                }
                p("blurb") {
                    +"A pedagogical Ktor service. Each route teaches one Kotlin language feature. "
                    +"Click "
                    code { +"Run ▶" }
                    +" on any card to call the live endpoint. "
                    a(href = "https://github.com/MichaelLambGelo/kotlin-tutorial", target = "_blank") {
                        attributes["rel"] = "noopener"
                        +"Source on GitHub →"
                    }
                }
                nav {
                    a(href = "#tour") { +"Tour" }
                    a(href = "#widgets") { +"Widgets" }
                    a(href = "#notes") { +"Notes" }
                    a(href = "#health") { +"Health" }
                    a(href = "/swagger") { +"Swagger ↗" }
                }
            }
            main {
                section("group") {
                    id = "tour"
                    h2 { +"Tour" }
                    p("group-blurb") { +"One file per language feature in src/main/kotlin/.../tour/." }
                    div("cards") {
                        tourEndpoints.forEach { renderEndpointCard(it) }
                    }
                }
                section("group") {
                    id = "widgets"
                    h2 { +"Widgets" }
                    p("group-blurb") { +"Server-rendered HTML fragments consumed by the blog. Embedded live below." }
                    div("widget-grid") {
                        widgetCards.forEach { (path, name, summary) ->
                            div("widget-card") {
                                attributes["data-widget-src"] = path
                                div("card-head") {
                                    span("method get") { +"GET" }
                                    span("path") { +path }
                                }
                                p("summary") { +summary }
                                div("widget-mount") { +"loading…" }
                            }
                        }
                    }
                }
                section("group") {
                    id = "notes"
                    h2 { +"Notes (in-memory CRUD)" }
                    p("group-blurb") {
                        +"Exercises all five "
                        code { +"/notes" }
                        +" verbs. Data lives in a "
                        code { +"ConcurrentHashMap" }
                        +" — restarts wipe everything."
                    }
                    div("notes-app") {
                        div("notes-form") {
                            h3 {
                                id = "notes-form-title"
                                +"Create note"
                            }
                            input(type = kotlinx.html.InputType.hidden) { id = "note-edit-id" }
                            div("field") {
                                label { htmlFor = "note-title"; +"Title" }
                                input(type = kotlinx.html.InputType.text) {
                                    id = "note-title"
                                    placeholder = "shopping list"
                                }
                            }
                            div("field") {
                                label { htmlFor = "note-body"; +"Body" }
                                textArea {
                                    id = "note-body"
                                    rows = "3"
                                    placeholder = "milk, eggs, kotlin"
                                }
                            }
                            div("button-row") {
                                button(type = ButtonType.button, classes = "primary") {
                                    id = "note-submit"
                                    attributes["onclick"] = "notesSubmit()"
                                    +"Create (POST)"
                                }
                                button(type = ButtonType.button, classes = "ghost") {
                                    id = "note-reset"
                                    attributes["onclick"] = "notesResetForm()"
                                    +"Cancel"
                                }
                            }
                        }
                        div("notes-list-wrap") {
                            div("button-row") {
                                button(type = ButtonType.button, classes = "ghost") {
                                    attributes["onclick"] = "notesRefresh()"
                                    +"Refresh list (GET /notes)"
                                }
                            }
                            ul("notes-list") {
                                id = "notes-list"
                                li("empty") { +"(no notes yet — create one)" }
                            }
                        }
                    }
                }
                section("group") {
                    id = "health"
                    h2 { +"Health" }
                    div("cards") {
                        renderEndpointCard(
                            EndpointCard(
                                method = "GET",
                                path = "/health",
                                summary = "Liveness check used by the Docker HEALTHCHECK and the badge above.",
                                snippet = """
                                    get("/health") {
                                      val uptime = (System.currentTimeMillis() - startTimeMillis) / 1000
                                      call.respond(HealthResponse("ok", uptime, "0.1.0"))
                                    }
                                """.trimIndent(),
                            ),
                        )
                    }
                }
            }
            footer("page-footer") {
                p {
                    +"Kotlin 2 · Ktor 3 · JDK 21 · "
                    a(href = "https://github.com/MichaelLambGelo/kotlin-tutorial") {
                        attributes["rel"] = "noopener"
                        +"github.com/MichaelLambGelo/kotlin-tutorial"
                    }
                }
            }
            script { unsafe { +PAGE_JS } }
        }
    }
    return "<!DOCTYPE html>\n$body"
}

private fun kotlinx.html.FlowContent.renderEndpointCard(card: EndpointCard) {
    div("card") {
        attributes["data-method"] = card.method
        attributes["data-path"] = card.path
        div("card-head") {
            span("method ${card.method.lowercase()}") { +card.method }
            span("path") { +card.path }
        }
        p("summary") { +card.summary }
        details {
            summary { +"source" }
            pre("kotlin") { code { +card.snippet } }
        }
        if (card.requestBody != null) {
            div("field") {
                label { +"request body" }
                textArea(classes = "request-body") {
                    rows = "3"
                    +card.requestBody
                }
            }
        }
        div("button-row") {
            button(type = ButtonType.button, classes = "primary run") {
                attributes["onclick"] = "runEndpoint(this)"
                +"Run ▶"
            }
        }
        pre("result") { +"" }
    }
}

private val PAGE_CSS = """
:root {
  --bg: #1e1f22;
  --bg-elev: #2b2d30;
  --bg-elev-2: #313438;
  --border: #3a3d41;
  --text: #bcbec4;
  --text-dim: #868a91;
  --kw: #cf8e6d;
  --str: #6aab73;
  --num: #2aacb8;
  --com: #7a7e85;
  --get: #6aab73;
  --post: #cf8e6d;
  --put: #6897bb;
  --delete: #c75450;
  --accent: #5394ec;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body {
  background: var(--bg);
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 14px;
  line-height: 1.55;
}
a { color: var(--accent); text-decoration: none; }
a:hover { text-decoration: underline; }
code { font-family: inherit; background: var(--bg-elev); padding: 1px 5px; border-radius: 3px; }

.page-header {
  border-bottom: 1px solid var(--border);
  padding: 24px 32px 16px;
  position: sticky;
  top: 0;
  background: var(--bg);
  z-index: 10;
}
.title-row { display: flex; align-items: center; gap: 16px; }
.page-header h1 { margin: 0; font-size: 22px; color: #e6e6e6; font-weight: 600; }
.health-badge {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  color: var(--text-dim);
}
.health-badge.ok { color: var(--get); border-color: var(--get); }
.health-badge.err { color: var(--delete); border-color: var(--delete); }
.blurb { color: var(--text-dim); margin: 8px 0 12px; max-width: 80ch; }
nav { display: flex; gap: 16px; font-size: 13px; }
nav a { color: var(--text-dim); }
nav a:hover { color: var(--accent); }

main { padding: 24px 32px 64px; max-width: 1200px; margin: 0 auto; }
.group { margin-top: 32px; }
.group h2 { font-size: 16px; color: #e6e6e6; margin: 0 0 4px; font-weight: 600; }
.group-blurb { color: var(--text-dim); margin: 0 0 16px; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }
.card {
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.card-head { display: flex; align-items: center; gap: 10px; }
.method {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 3px;
  letter-spacing: 0.5px;
}
.method.get { background: rgba(106,171,115,0.15); color: var(--get); }
.method.post { background: rgba(207,142,109,0.15); color: var(--post); }
.method.put { background: rgba(104,151,187,0.15); color: var(--put); }
.method.delete { background: rgba(199,84,80,0.15); color: var(--delete); }
.path { color: #e6e6e6; word-break: break-all; }
.summary { color: var(--text-dim); margin: 0; font-size: 13px; }

details { background: var(--bg-elev-2); border-radius: 4px; padding: 6px 10px; }
details summary { cursor: pointer; color: var(--text-dim); font-size: 12px; user-select: none; }
details[open] summary { margin-bottom: 6px; }
pre.kotlin {
  margin: 0;
  padding: 10px;
  background: #181a1d;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12.5px;
  line-height: 1.5;
}
pre.kotlin .kw { color: var(--kw); }
pre.kotlin .str { color: var(--str); }
pre.kotlin .num { color: var(--num); }
pre.kotlin .com { color: var(--com); font-style: italic; }

.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: 11px; color: var(--text-dim); text-transform: uppercase; letter-spacing: 0.5px; }
textarea, input[type=text] {
  font-family: inherit;
  font-size: 13px;
  background: #181a1d;
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 8px 10px;
  resize: vertical;
}
textarea:focus, input[type=text]:focus { outline: none; border-color: var(--accent); }

.button-row { display: flex; gap: 8px; flex-wrap: wrap; }
button {
  font-family: inherit;
  font-size: 13px;
  padding: 6px 14px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid var(--border);
  background: var(--bg-elev-2);
  color: var(--text);
}
button:hover { border-color: var(--accent); color: #fff; }
button.primary { background: var(--accent); border-color: var(--accent); color: #fff; }
button.primary:hover { background: #6aa3f0; }
button.ghost { background: transparent; }
button.danger { color: var(--delete); border-color: var(--delete); }

pre.result {
  margin: 0;
  padding: 10px;
  background: #181a1d;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12.5px;
  min-height: 0;
  color: var(--text);
  white-space: pre-wrap;
  word-break: break-word;
}
pre.result:empty { display: none; }
pre.result.err { color: var(--delete); }

.widget-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 16px; }
.widget-card {
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.widget-mount { background: #181a1d; border-radius: 4px; padding: 10px; min-height: 100px; color: var(--text-dim); }

.notes-app { display: grid; grid-template-columns: minmax(280px, 1fr) 2fr; gap: 16px; }
@media (max-width: 720px) { .notes-app { grid-template-columns: 1fr; } }
.notes-form, .notes-list-wrap {
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 6px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.notes-form h3 { margin: 0; font-size: 14px; color: #e6e6e6; font-weight: 600; }
.notes-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.notes-list li {
  background: var(--bg-elev-2);
  padding: 8px 10px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.notes-list li.empty { color: var(--text-dim); background: transparent; padding: 4px 0; }
.notes-list .note-title { flex: 1; color: #e6e6e6; }
.notes-list .note-id { color: var(--text-dim); font-size: 11px; }

.page-footer {
  border-top: 1px solid var(--border);
  padding: 16px 32px;
  color: var(--text-dim);
  font-size: 12px;
  text-align: center;
}
""".trimIndent()

private val PAGE_JS = """
const KW = /\b(fun|val|var|if|else|when|is|return|sealed|data|class|object|interface|suspend|coroutineScope|async|await|listOf|mapOf|with|let|run|apply|also|true|false|null|in|for|while|inline|reified|out|override|infix|by|runCatching|generateSequence)\b/g;
const STR = /"(?:[^"\\]|\\.)*"/g;
const NUM = /\b\d+(?:\.\d+)?\b/g;
const COM = /\/\/[^\n]*/g;

function escapeHtml(s) {
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

function highlight(code) {
  // Tokenise once into a flat list to avoid double-substitution.
  const tokens = [];
  const pattern = /(\/\/[^\n]*)|("(?:[^"\\]|\\.)*")|(\b\d+(?:\.\d+)?\b)|(\b(?:fun|val|var|if|else|when|is|return|sealed|data|class|object|interface|suspend|coroutineScope|async|await|listOf|mapOf|with|let|run|apply|also|true|false|null|in|for|while|inline|reified|out|override|infix|by|runCatching|generateSequence)\b)/g;
  let last = 0, m;
  let out = '';
  while ((m = pattern.exec(code)) !== null) {
    out += escapeHtml(code.slice(last, m.index));
    if (m[1]) out += '<span class="com">' + escapeHtml(m[1]) + '</span>';
    else if (m[2]) out += '<span class="str">' + escapeHtml(m[2]) + '</span>';
    else if (m[3]) out += '<span class="num">' + escapeHtml(m[3]) + '</span>';
    else if (m[4]) out += '<span class="kw">' + escapeHtml(m[4]) + '</span>';
    last = m.index + m[0].length;
  }
  out += escapeHtml(code.slice(last));
  return out;
}

function highlightAll() {
  document.querySelectorAll('pre.kotlin code').forEach(el => {
    el.innerHTML = highlight(el.textContent);
  });
}

async function runEndpoint(btn) {
  const card = btn.closest('.card');
  const method = card.dataset.method;
  const path = card.dataset.path;
  const result = card.querySelector('pre.result');
  const bodyEl = card.querySelector('textarea.request-body');
  result.classList.remove('err');
  result.textContent = 'loading…';
  try {
    const init = { method, headers: {} };
    if (bodyEl) {
      init.headers['Content-Type'] = 'application/json';
      init.body = bodyEl.value;
    }
    const t0 = performance.now();
    const res = await fetch(path, init);
    const ms = Math.round(performance.now() - t0);
    const ct = res.headers.get('content-type') || '';
    let bodyText;
    if (ct.includes('application/json')) {
      bodyText = JSON.stringify(await res.json(), null, 2);
    } else {
      bodyText = await res.text();
    }
    result.textContent = '// ' + res.status + ' ' + res.statusText + ' · ' + ms + 'ms\n' + bodyText;
    if (!res.ok) result.classList.add('err');
  } catch (err) {
    result.classList.add('err');
    result.textContent = 'fetch failed: ' + err.message;
  }
}

async function loadHealth() {
  const badge = document.getElementById('health-badge');
  try {
    const res = await fetch('/health');
    const json = await res.json();
    badge.textContent = json.status + ' · v' + json.version + ' · up ' + json.uptimeSeconds + 's';
    badge.classList.add('ok');
  } catch (err) {
    badge.textContent = 'down';
    badge.classList.add('err');
  }
}

async function loadWidgets() {
  document.querySelectorAll('.widget-card').forEach(async card => {
    const src = card.dataset.widgetSrc;
    const mount = card.querySelector('.widget-mount');
    try {
      const res = await fetch(src);
      mount.innerHTML = await res.text();
    } catch (err) {
      mount.textContent = 'failed to load ' + src + ': ' + err.message;
    }
  });
}

async function notesRefresh() {
  const list = document.getElementById('notes-list');
  try {
    const res = await fetch('/notes');
    const notes = await res.json();
    if (!notes.length) {
      list.innerHTML = '<li class="empty">(no notes yet — create one)</li>';
      return;
    }
    list.innerHTML = '';
    notes.forEach(n => {
      const li = document.createElement('li');
      li.innerHTML =
        '<span class="note-title"></span> ' +
        '<span class="note-id"></span> ' +
        '<button class="ghost" data-act="edit">edit</button> ' +
        '<button class="ghost danger" data-act="del">delete</button>';
      li.querySelector('.note-title').textContent = n.title;
      li.querySelector('.note-id').textContent = n.id.slice(0, 8);
      li.querySelector('[data-act=edit]').onclick = () => notesStartEdit(n);
      li.querySelector('[data-act=del]').onclick = () => notesDelete(n.id);
      list.appendChild(li);
    });
  } catch (err) {
    list.innerHTML = '<li class="empty">failed: ' + err.message + '</li>';
  }
}

function notesStartEdit(n) {
  document.getElementById('notes-form-title').textContent = 'Edit note · ' + n.id.slice(0, 8);
  document.getElementById('note-edit-id').value = n.id;
  document.getElementById('note-title').value = n.title;
  document.getElementById('note-body').value = n.body;
  document.getElementById('note-submit').textContent = 'Update (PUT)';
}

function notesResetForm() {
  document.getElementById('notes-form-title').textContent = 'Create note';
  document.getElementById('note-edit-id').value = '';
  document.getElementById('note-title').value = '';
  document.getElementById('note-body').value = '';
  document.getElementById('note-submit').textContent = 'Create (POST)';
}

async function notesSubmit() {
  const id = document.getElementById('note-edit-id').value;
  const title = document.getElementById('note-title').value;
  const body = document.getElementById('note-body').value;
  const init = {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, body }),
  };
  const path = id ? '/notes/' + id : '/notes';
  const res = await fetch(path, init);
  if (!res.ok) {
    alert((id ? 'Update' : 'Create') + ' failed: ' + res.status);
    return;
  }
  notesResetForm();
  notesRefresh();
}

async function notesDelete(id) {
  if (!confirm('Delete note ' + id.slice(0, 8) + '?')) return;
  const res = await fetch('/notes/' + id, { method: 'DELETE' });
  if (!res.ok && res.status !== 204) {
    alert('Delete failed: ' + res.status);
    return;
  }
  notesRefresh();
}

document.addEventListener('DOMContentLoaded', () => {
  highlightAll();
  loadHealth();
  loadWidgets();
  notesRefresh();
});
""".trimIndent()
