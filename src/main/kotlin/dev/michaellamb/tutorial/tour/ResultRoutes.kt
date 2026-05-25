package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.math.sqrt

// Kotlin: error handling with Result.
// `runCatching { }` turns a throwing call into a Result<T> — success or failure as a
// value, no try/catch at the call site. `fold` handles both arms; `getOrElse` recovers
// with a fallback. Custom exceptions are ordinary classes extending a Throwable.
private class NegativeInputException(n: Int) :
    IllegalArgumentException("cannot take sqrt of negative number: $n")

private fun checkedSqrt(n: Int): Double {
    if (n < 0) throw NegativeInputException(n)
    return sqrt(n.toDouble())
}

fun Route.resultRoutes() {
    get("/result") {
        val n = call.queryParameters["n"]?.toIntOrNull() ?: 16

        // One input, wrapped: success or failure folded into a string.
        val folded = runCatching { checkedSqrt(n) }
            .fold(
                onSuccess = { "ok: $it" },
                onFailure = { "error: ${it.message}" },
            )

        // Recover from failure with a fallback value.
        val recovered = runCatching { checkedSqrt(-1) }.getOrElse { Double.NaN }

        // A batch: each element becomes its own Result, then a label.
        val batch = listOf(16, -4, 9).map { runCatching { checkedSqrt(it) } }
            .map { result -> result.fold({ "√ = $it" }, { "failed: ${it.message}" }) }

        call.respond(
            mapOf(
                "input" to n,
                "folded" to folded,
                "recovered_to_nan" to recovered.isNaN(),
                "batch" to batch,
            )
        )
    }
}
