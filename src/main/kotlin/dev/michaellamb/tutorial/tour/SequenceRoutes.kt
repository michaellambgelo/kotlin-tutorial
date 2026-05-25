package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: sequences (lazy evaluation).
// A List runs each step over the WHOLE collection before the next (eager). A Sequence
// pulls one element through the whole chain at a time (lazy), so `first { }` can
// short-circuit. `generateSequence` builds an infinite stream you slice with `take`.
fun Route.sequenceRoutes() {
    get("/sequences") {
        // Count how often the map lambda runs to reach the first match > 1000.
        var eagerCount = 0
        val eagerResult = (1..1_000)
            .map { eagerCount++; it * it } // squares ALL 1000 first
            .first { it > 1_000 }

        var lazyCount = 0
        val lazyResult = (1..1_000)
            .asSequence()
            .map { lazyCount++; it * it } // squares only until the first match
            .first { it > 1_000 }

        // Infinite Fibonacci stream, sliced to the first 10.
        val fibonacci = generateSequence(0 to 1) { (a, b) -> b to (a + b) }
            .map { it.first }
            .take(10)
            .toList()

        call.respond(
            mapOf(
                "first_square_over_1000" to lazyResult,
                "eager_lambda_invocations" to eagerCount, // 1000
                "lazy_lambda_invocations" to lazyCount, // 32 — stops early
                "same_result" to (eagerResult == lazyResult),
                "fibonacci_first_10" to fibonacci,
            )
        )
    }
}
