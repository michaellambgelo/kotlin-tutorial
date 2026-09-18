/*
 * The endpoint catalog — one description of this service's routes, consumed by two surfaces.
 *
 * Pedagogical focus: a single source of truth rendered two ways. The home page (home/HomeRoutes.kt)
 * turns these into interactive cards; the OpenAPI post-processor (plugins/OpenApi.kt) folds the same
 * summaries into the generated spec. Keeping them in one place is what lets a card link straight at
 * its own Swagger operation — [swaggerHref] and the spec's operationId are both minted from
 * [operationIdFor], so the two surfaces cannot drift apart.
 *
 * These are top-level `internal` declarations rather than members of an object: the card list is
 * ~380 lines of raw-string snippets, and nesting it would re-indent every one of them for no gain.
 */
package dev.michaellamb.tutorial.catalog

/** A server-rendered HTML fragment embedded live on the home page. */
internal data class WidgetCard(
    val path: String,
    val name: String,
    val summary: String,
)

internal data class EndpointCard(
    val method: String,
    val path: String,
    val summary: String,
    val snippet: String,
    val requestBody: String? = null,
) {
    /** The path as the OpenAPI spec keys it — the spec never carries the demo query string. */
    val specPath: String get() = path.substringBefore('?')
}

// Ordered to follow the official Kotlin tour (kotlinlang.org/docs/kotlin-tour-welcome.html):
// the beginner chapters, then the intermediate ones, then what this service adds on top.
internal val tourEndpoints: List<EndpointCard> = listOf(
    // ---- Beginner tour ----
    EndpointCard(
        method = "GET",
        path = "/tour/variables?name=Kodee",
        summary = "val vs var, type inference, string templates.",
        snippet = """
            val name = "Kodee"        // read-only, type inferred
            var counter = 0           // reassignable
            val declared: Long = 42   // explicit — no implicit widening

            "Hello, ${'$'}name!"                              // template
            "${'$'}name has ${'$'}{name.length} characters"       // expression
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/basic-types",
        summary = "Int / Long / Double / Boolean / Char / String and explicit conversion.",
        snippet = """
            val whole = 100          // Int
            val big = 100L           // Long
            val letter = 'K'         // Char, not a String

            whole.toLong() + big     // conversions are always explicit
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/collection-types",
        summary = "List / Set / Map, read-only by default and their mutable siblings.",
        snippet = """
            val readOnly = listOf("green", "red")     // no add() at all
            val mutable = mutableListOf("green").apply { add("yellow") }

            setOf("a", "b", "a").size                 // 2 — duplicates collapse
            mapOf("kiwi" to 190)["durian"]            // null, doesn't throw
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/control-flow?n=7",
        summary = "if/when as expressions, ranges, for and while loops.",
        snippet = """
            val parity = if (n % 2 == 0) "even" else "odd"   // no ternary needed

            val size = when (n) {
              0 -> "nothing"
              in 2..9 -> "a handful"
              else -> "a lot"
            }

            for (i in 5 downTo 1 step 2) { /* 5, 3, 1 */ }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/functions?name=Han%20Solo",
        summary = "Default parameters, named arguments, single-expression bodies, early returns.",
        snippet = """
            fun greet(name: String, greeting: String = "Hello") = "${'$'}greeting, ${'$'}name"

            greet(name, excited = true)          // skip the middle parameter
            greet(greeting = "Howdy", name = n)  // any order, once named

            fun square(n: Int) = n * n           // single expression, inferred type
        """.trimIndent(),
    ),
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

    // ---- Intermediate tour ----
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
        path = "/tour/lambdas-with-receiver",
        summary = "Lambdas with receiver — the shape behind every Kotlin builder DSL.",
        snippet = """
            fun menu(name: String, init: Menu.() -> Unit) = Menu(name).apply(init)

            menu("Breakfast") {
              item("Coffee", 350)      // `this` is the Menu — no qualifier
              item("Pancakes", 900)
            }
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
        path = "/tour/delegation",
        summary = "Interface delegation — `by` writes the forwarding boilerplate for you.",
        snippet = """
            class RedPen(private val base: DrawingTool = PenTool()) :
              DrawingTool by base {
                override val color = "red"
                override fun draw(shape: String) = "drawing ${'$'}shape in ${'$'}color"
              }
            // erase() and info() are forwarded to `base`, never written here
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/objects",
        summary = "object declarations — singletons, data objects, companion objects.",
        snippet = """
            object Registry { fun register(name: String): Int { /* ... */ } }

            data object AppConfig { const val VERSION = "1.0.0" }

            class Temperature private constructor(val celsius: Double) {
              companion object { fun fromFahrenheit(f: Double) = /* ... */ }
            }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/open-classes",
        summary = "Classes are final by default — open, abstract, override, super.",
        snippet = """
            abstract class Vehicle(val name: String, val wheels: Int) {
              abstract fun sound(): String
              open fun describe() = "${'$'}name goes ${'$'}{sound()}"
            }

            class RaceCar(name: String) : Car(name) {
              override fun describe() = super.describe() + " (at speed)"
            }
        """.trimIndent(),
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
        path = "/tour/enums?planet=jupiter",
        summary = "enum class — per-constant state, overrides, entries, exhaustive when.",
        snippet = """
            enum class Planet(val radiusKm: Double, val gravity: Double) {
              EARTH(6371.0, 9.81),
              JUPITER(69911.0, 24.79) {
                override fun blurb() = "big and stormy"
              };
              open fun blurb() = "radius ${'$'}{radiusKm}km"
            }

            Planet.entries.find { it.name == requested }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/value-classes",
        summary = "@JvmInline value class — distinct types, no wrapper allocation.",
        snippet = """
            @JvmInline
            value class UserId(val raw: String) {
              init { require(raw.isNotBlank()) }
            }

            fun invite(id: UserId, email: EmailAddress)
            // invite(email, id) does not compile — both are Strings only at runtime
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/properties",
        summary = "Backing fields, extension properties, by lazy, Delegates.observable.",
        snippet = """
            var name: String = ""
              set(value) { field = value.trim() }   // `field`, not `name` — no recursion

            val String.lastChar: Char get() = this[length - 1]

            val token: String by lazy { expensive() }              // computed once
            var logLevel by Delegates.observable("INFO") { p, o, n -> log(o, n) }
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/smart-casts?value=luke",
        summary = "Smart casts, as? safe casts, and null-aware collection operators.",
        snippet = """
            when (input) {
              is Int -> input * 2                 // smart cast, no explicit cast
              is String -> input.uppercase()
            }

            (anything as? String)?.uppercase() ?: "not a String"
            listOf("a", null, "b").filterNotNull()
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/opt-in?text=kotlin",
        summary = "@RequiresOptIn / @OptIn, plus kotlin.time Durations.",
        snippet = """
            @RequiresOptIn(level = RequiresOptIn.Level.ERROR)
            annotation class ExperimentalTutorialApi

            @OptIn(ExperimentalTutorialApi::class, ExperimentalUnsignedTypes::class)
            fun demo() = uintArrayOf(1u, 2u, 3u)

            (90.minutes + 500.milliseconds).toIsoString()   // PT1H30M0.500S
        """.trimIndent(),
    ),

    // ---- Beyond the tour ----
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
        path = "/tour/comparators",
        summary = "Comparable vs Comparator, compareBy/thenBy, null placement, sort stability.",
        snippet = """
            films.sortedWith(
              compareByDescending<Film> { it.rating ?: -1.0 }.thenBy { it.year }
            )

            compareBy(nullsLast()) { it.rating }   // place nulls, don't fake them
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/flow",
        summary = "Cold streams — a Sequence whose producer may suspend. Operators, backpressure, hot vs cold.",
        snippet = """
            val numbers = flow {
              for (i in 1..5) { delay(1); emit(i) }   // delay() is illegal in a Sequence
            }

            numbers.toList()                 // runs the builder
            numbers.map { it * 10 }.toList() // runs it AGAIN — cold
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/operators",
        summary = "operator fun — plus, times, get/set, invoke, contains, compareTo, and == vs ===.",
        snippet = """
            operator fun plus(other: Money) = Money(cents + other.cents)
            operator fun get(item: String): Money       // cart["coffee"]
            operator fun invoke(): Money                // cart()
            override fun compareTo(other: Money): Int   // gives < > <= >= at once
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/property-delegates",
        summary = "Writing your own `by` delegate: getValue/setValue and the KProperty you're handed.",
        snippet = """
            class Clamped(var v: Int, val range: IntRange) : ReadWriteProperty<Any?, Int> {
              override fun getValue(r: Any?, p: KProperty<*>) = v
              override fun setValue(r: Any?, p: KProperty<*>, value: Int) { v = value.coerceIn(range) }
            }

            var volume: Int by Clamped(5, 0..10)
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/type-alias",
        summary = "typealias is a NAME, not a type — the contrast with value classes.",
        snippet = """
            typealias Validator<T> = (T) -> String?   // alias: interchangeable with the function type
            value class UserId(val v: String)         // new type: mix-ups fail to compile
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/recursion",
        summary = "tailrec as a checked claim, plus nested vs inner classes.",
        snippet = """
            tailrec fun sumTo(n: Long, acc: Long = 0): Long =
              if (n == 0L) acc else sumTo(n - 1, acc + n)   // rewritten to a loop

            class Metadata          // nested: no outer reference
            inner class Entry       // inner: captures this@Chapter
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/contracts",
        summary = "How isNullOrBlank() smart-casts — telling the compiler what your function proved.",
        snippet = """
            fun isPresent(value: String?): Boolean {
              contract { returns(true) implies (value != null) }
              return !value.isNullOrBlank()
            }

            if (isPresent(x)) { x.length }   // smart-cast, purely from the contract
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/context-parameters",
        summary = "Kotlin 2.2 preview — implicit, named dependencies for a whole call tree.",
        snippet = """
            context(ctx: RequestContext, audit: AuditLog)
            fun publish(title: String): String { ... }

            with(ctx) { with(audit) { publish("post") } }   // supplied implicitly
        """.trimIndent(),
    ),
    EndpointCard(
        method = "GET",
        path = "/tour/java-interop",
        summary = "Platform types (String!), and @JvmStatic/@JvmOverloads for callers on the Java side.",
        snippet = """
            val s: String  = System.getenv("X")   // String! — no null check, NPE deferred
            val t: String? = System.getenv("X")   // both compile; only one is honest

            @JvmOverloads fun greet(name: String, greeting: String = "Hello")
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

internal val widgetCards: List<WidgetCard> = listOf(
    WidgetCard("/widgets/letterboxd", "Letterboxd", "Recent watches via RSS — kotlinx.html DSL fragment, 60s cache."),
    WidgetCard("/widgets/steam", "Steam", "Recently played games via Steam Web API — graceful fallback if STEAM_API_KEY unset."),
    WidgetCard("/widgets/cluster", "Cluster", "Homelab status via Uptime Kuma — parallel async fetches with structured concurrency."),
    WidgetCard("/widgets/github", "GitHub", "Recent commits (last 24h) via the GitHub search API — java.time rolling window + groupBy repo."),
    WidgetCard("/widgets/recently-updated", "Recently updated", "Hand-curated blurbs from a KV-backed Cloudflare Worker behind Cloudflare Access — an `object` client issuing authenticated GET/POST/DELETE."),
)

/** The liveness endpoint. Rendered as its own home-page section, and tagged `Service` in the spec. */
internal val healthCard = EndpointCard(
    method = "GET",
    path = "/health",
    summary = "Liveness check used by the Docker HEALTHCHECK and the badge above.",
    snippet = """
        get("/health") {
          val uptime = (System.currentTimeMillis() - startTimeMillis) / 1000
          call.respond(HealthResponse("ok", uptime, "0.1.0"))
        }
    """.trimIndent(),
)

// Summaries for the endpoints that have no home-page card but do appear in the generated spec.
// Without these their Swagger rows render as a bare path with no prose — the compiler plugin emits
// `summary: ""` for every operation (it only fills one in from an explicit KDoc annotation).
private val uncardedSummaries: Map<Pair<String, String>, String> = mapOf(
    ("GET" to "/") to "This page — a kotlinx.html DSL document listing every route.",
    ("GET" to "/signage") to "Full-page TV view of the /now digest, cast to a display via homelab-bot.",
    ("POST" to "/demo-auth/token") to "Mint a demo JWT. Guards nothing — the real /admin is behind Cloudflare Access.",
    ("GET" to "/demo-auth/decode") to "Read a JWT's claims WITHOUT the secret — signing is integrity, not confidentiality.",
    ("GET" to "/demo-auth/whoami") to "Requires a Bearer token; shows the validated principal.",
    ("GET" to "/htmx") to "htmx demo — the home page's Run buttons, with the JavaScript deleted.",
    ("GET" to "/htmx/chapter/{name}") to "An HTML fragment for htmx to splice in — not a full document.",
    ("GET" to "/metrics") to "Prometheus scrape target — request timings recorded by the Micrometer plugin.",
    ("GET" to "/archive") to "Type-safe routing: the tour index, filtered from an @Resource class rather than call.parameters.",
    ("GET" to "/archive/{name}") to "One tour chapter, addressed by a nested @Resource that keeps its parent's query params.",
    ("GET" to "/stream/now") to "Server-Sent Events: the /now digest as a cold Flow, deduplicated and pushed on change.",
    ("GET" to "/ws/now") to "WebSocket sibling of /stream/now — same Flow, duplex transport.",
    ("GET" to "/widgets/now.json") to "Every /now section as one JSON document, fetched concurrently and degraded per-section.",
    ("GET" to "/widgets/projects") to "The curated Projects list for the blog's /about page, read from the local SQLite store.",
    ("GET" to "/notes") to "List every persisted note, newest first.",
    ("POST" to "/notes") to "Create a note. Persisted to SQLite via Exposed — it survives restarts.",
    ("GET" to "/notes/{id}") to "Fetch one note by id.",
    ("PUT" to "/notes/{id}") to "Update a note's title and/or body.",
    ("DELETE" to "/notes/{id}") to "Delete a note.",
    ("GET" to "/admin") to "Admin form for the curated \"Recently updated\" feed. Gated by Cloudflare Access.",
    ("POST" to "/admin/entries") to "Publish a \"Recently updated\" entry to the now-store Worker.",
    ("POST" to "/admin/entries/{id}/delete") to "Delete a \"Recently updated\" entry and retract its Discord embed.",
    ("GET" to "/admin/projects") to "Admin form for the Projects list. Gated by Cloudflare Access.",
    ("POST" to "/admin/projects") to "Create a Projects record.",
    ("POST" to "/admin/projects/{id}/update") to "Update a Projects record.",
    ("POST" to "/admin/projects/{id}/delete") to "Delete a Projects record.",
    ("POST" to "/admin/projects/{id}/move") to "Reorder a Projects record by swapping its position with its neighbour.",
)

/**
 * (METHOD, specPath) -> one-line summary, folded into the generated OpenAPI spec by
 * `plugins/OpenApi.kt`. Card summaries win; [uncardedSummaries] fills the rest.
 */
internal val endpointSummaries: Map<Pair<String, String>, String> =
    uncardedSummaries +
        widgetCards.associate { ("GET" to it.path) to it.summary } +
        (tourEndpoints + healthCard).associate { (it.method to it.specPath) to it.summary }

/**
 * Which chapter each tour route belongs to, mirroring the grouping in [tourEndpoints] and the mount
 * order in `plugins/Routing.kt`. Consumed by `resources/TourArchive.kt`.
 *
 * `EndpointCatalogTest` asserts this covers exactly the tour cards — nothing missing, nothing stale
 * — so adding a route without listing it here is a test failure rather than a silent gap.
 */
internal val tourChapters: Map<String, List<String>> = mapOf(
    "beginner" to listOf(
        "/tour/variables", "/tour/basic-types", "/tour/collection-types", "/tour/control-flow",
        "/tour/functions", "/tour/data-class", "/tour/null-safety",
    ),
    "intermediate" to listOf(
        "/tour/extensions", "/tour/scope-functions", "/tour/lambdas-with-receiver", "/tour/interfaces",
        "/tour/delegation", "/tour/objects", "/tour/open-classes", "/tour/sealed-when", "/tour/enums",
        "/tour/value-classes", "/tour/properties", "/tour/smart-casts", "/tour/opt-in",
    ),
    "beyond" to listOf(
        "/tour/collections", "/tour/higher-order-functions", "/tour/comparators", "/tour/sequences",
        "/tour/coroutines", "/tour/flow", "/tour/result", "/tour/generics", "/tour/operators",
        "/tour/property-delegates", "/tour/type-alias", "/tour/recursion", "/tour/contracts",
        "/tour/context-parameters", "/tour/reflection", "/tour/java-interop",
    ),
)

/**
 * Mirrors swagger-ui's own fallback id synthesis (`{method}{path with every non-word char as _}`),
 * so the anchors keep their familiar shape — but minting them ourselves makes the format our
 * contract rather than an internal of a CDN-pinned bundle that a Ktor bump could move.
 */
internal fun operationIdFor(method: String, specPath: String): String =
    method.lowercase() + specPath.replace(Regex("[^A-Za-z0-9]"), "_")

/**
 * The Swagger UI tag a path belongs to. Single URL-safe words on purpose: swagger-ui
 * `encodeURIComponent`s both anchor segments, so a space or a slash here would silently
 * change every href built by [swaggerHref].
 */
internal fun tagFor(specPath: String): String = when {
    specPath.startsWith("/tour/") -> "Tour"
    // The archive is a typed index *of* the tour, so it belongs in that section.
    specPath.startsWith("/archive") -> "Tour"
    specPath.startsWith("/stream/") || specPath.startsWith("/ws/") -> "Streams"
    specPath.startsWith("/htmx") -> "Service"
    specPath.startsWith("/demo-auth") -> "Auth"
    specPath.startsWith("/widgets/") -> "Widgets"
    specPath.startsWith("/notes") -> "Notes"
    specPath.startsWith("/admin") -> "Admin"
    else -> "Service" // /, /health, /signage
}

/** Tag order for the spec's document-level `tags` array — Swagger UI renders its sections in this
 *  order, so it mirrors the home page. */
internal val tagOrder: List<Pair<String, String>> = listOf(
    "Notes" to "CRUD persisted to SQLite on disk via Exposed.",
    "Widgets" to "Server-rendered HTML fragments consumed by the blog, plus their JSON aggregation.",
    "Tour" to "One endpoint per Kotlin language feature, following the official Kotlin tour.",
    "Streams" to "Long-lived connections: Server-Sent Events and WebSockets over a cold Flow.",
    "Service" to "The home page, liveness check, and the TV signage view.",
    "Auth" to "A JWT demonstration that protects nothing — the real gate is Cloudflare Access.",
    "Admin" to "Publish/edit forms, gated at the edge by Cloudflare Access.",
)

/**
 * A deep link into the Swagger UI, landing on exactly this operation.
 *
 * `docExpansion=none` is a query parameter Ktor reads server-side (not a SwaggerConfig property),
 * and it collapses everything the deep link does not target. The `#/{tag}/{operationId}` fragment
 * is only honoured on a full page load — swagger-ui ignores a hash change on an already-rendered
 * page — which is fine for a link out of the home page.
 *
 * Requires `deepLinking = true` in the `swaggerUI { }` block; without it the fragment is inert.
 */
internal fun swaggerHref(method: String, path: String): String {
    val spec = path.substringBefore('?')
    return "/swagger?docExpansion=none#/${tagFor(spec)}/${operationIdFor(method, spec)}"
}

/** A deep link to a whole Swagger UI section. */
internal fun swaggerTagHref(tag: String): String = "/swagger?docExpansion=none#/$tag"
