package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: extension functions.
// Add methods to types you don't own. The receiver (`this`) is the value before the dot.
// Resolved statically at the call site — these are syntactic sugar over a static helper.
fun String.toSlug(): String =
    lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")

fun Int.isPrime(): Boolean {
    if (this < 2) return false
    if (this < 4) return true
    if (this % 2 == 0) return false
    var i = 3
    while (i * i <= this) {
        if (this % i == 0) return false
        i += 2
    }
    return true
}

fun Route.extensionRoutes() {
    get("/extensions") {
        val text = call.queryParameters["text"] ?: "Hello, Kotlin Tutorial!"
        val n = call.queryParameters["n"]?.toIntOrNull() ?: 17

        call.respond(
            mapOf(
                "input_text" to text,
                "slug" to text.toSlug(),
                "input_int" to n,
                "is_prime" to n.isPrime(),
                "primes_below_50" to (2..50).filter { it.isPrime() },
            )
        )
    }
}
