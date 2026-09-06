package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// Kotlin: opt-in requirements (and a slice of the stdlib worth knowing).
// A library marks an unstable API with an annotation that is itself annotated
// `@RequiresOptIn`. Calling it without acknowledgement is a warning or an error; you
// acknowledge with `@OptIn(Marker::class)` at the call site, which keeps "I know this may
// change" visible in the code rather than buried in a changelog.
@RequiresOptIn(message = "This tutorial API is experimental and may change.", level = RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
private annotation class ExperimentalTutorialApi

@ExperimentalTutorialApi
private fun experimentalReverse(text: String): String = text.reversed()

// The opt-in is explicit and local to the function that needs it.
@OptIn(ExperimentalTutorialApi::class, ExperimentalUnsignedTypes::class)
private fun demo(text: String): Map<String, Any> = mapOf(
    "experimental_reverse" to experimentalReverse(text),
    // uintArrayOf() is stdlib, and gated behind @ExperimentalUnsignedTypes.
    "unsigned_array" to uintArrayOf(1u, 2u, 3u).map { it.toLong() },
    "unsigned_max" to UInt.MAX_VALUE.toLong(), // wider than Int.MAX_VALUE
)

fun Route.optInRoutes() {
    get("/opt-in") {
        val text = call.queryParameters["text"] ?: "kotlin"

        // kotlin.time.Duration: a typed, unit-safe amount of time from the stdlib.
        val timeout = 90.minutes + 500.milliseconds

        call.respond(
            demo(text) + mapOf(
                "input" to text,
                "duration_iso" to timeout.toIsoString(),
                "duration_readable" to timeout.toString(),
                "duration_in_whole_minutes" to timeout.inWholeMinutes,
            )
        )
    }
}
