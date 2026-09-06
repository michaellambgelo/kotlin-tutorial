package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: control flow.
// There is no ternary operator because `if` is already an expression that yields a value,
// and so is `when` — Kotlin's switch, which matches on values, ranges, types, or (with no
// subject) arbitrary conditions. Ranges (`..`, `..<`, `downTo`, `step`) are values you can
// iterate, test with `in`, and pass around.
private fun classify(n: Int): String = when {
    n < 0 -> "negative"
    n == 0 -> "zero"
    n % 2 == 0 -> "positive even"
    else -> "positive odd"
}

fun Route.controlFlowRoutes() {
    get("/control-flow") {
        val n = call.queryParameters["n"]?.toIntOrNull() ?: 7

        // `if` as an expression — the branch value is the result, no ternary needed.
        val parity = if (n % 2 == 0) "even" else "odd"

        // `when` as an expression, matching literals and a range, with an else arm.
        val size = when (n) {
            0 -> "nothing"
            1 -> "one"
            in 2..9 -> "a handful"
            else -> "a lot"
        }

        // `for` over a range, and over a collection with an index.
        val squares = buildList { for (i in 1..5) add(i * i) }
        val countdown = buildList { for (i in 5 downTo 1 step 2) add(i) }

        // `while` — the one place a plain statement, not an expression, is the point.
        var doublings = 0
        var value = 1
        while (value < n) {
            value *= 2
            doublings++
        }

        call.respond(
            mapOf(
                "n" to n,
                "if_expression_parity" to parity,
                "when_expression_size" to size,
                "when_without_subject" to classify(n),
                "half_open_range_1_until_5" to (1..<5).toList(),
                "in_range_check" to (n in 1..10),
                "char_range" to ('a'..'e').toList().map { it.toString() },
                "for_squares" to squares,
                "for_downto_step_2" to countdown,
                "while_doublings_to_reach_n" to doublings,
            )
        )
    }
}
