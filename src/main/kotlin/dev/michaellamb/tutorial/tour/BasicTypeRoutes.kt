package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: basic types.
// Everything is an object — there are no primitives in the language, though the compiler
// still emits JVM primitives where it can. Numeric types do not implicitly widen: an Int
// is not a Long until you say so with `.toLong()`. `Char` is a single quote, `String` a
// double quote, and both are distinct types.
fun Route.basicTypeRoutes() {
    get("/basic-types") {
        val whole = 100 // Int
        val big = 100L // Long
        val fraction = 3.14 // Double
        val small = 3.14f // Float
        val flag = true // Boolean
        val letter = 'K' // Char
        val text = "Kotlin" // String

        call.respond(
            mapOf(
                "inferred_types" to mapOf(
                    "100" to whole::class.simpleName,
                    "100L" to big::class.simpleName,
                    "3.14" to fraction::class.simpleName,
                    "3.14f" to small::class.simpleName,
                    "true" to flag::class.simpleName,
                    "'K'" to letter::class.simpleName,
                    "\"Kotlin\"" to text::class.simpleName,
                ),
                // No implicit widening — the conversion is always explicit and visible.
                "explicit_widening" to (whole.toLong() + big),
                "int_range" to "${Int.MIN_VALUE}..${Int.MAX_VALUE}",
                "long_max" to Long.MAX_VALUE.toString(),
                "char_is_not_a_string" to (letter.toString() == "K"),
                "char_code" to letter.code,
            )
        )
    }
}
