package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: scope functions (let, run, with, apply, also).
// They differ on (a) what `this` and `it` refer to inside the block, and (b) what the
// expression returns. Same effect, five flavors — pick by what reads cleanest.
private data class GreetingBuilder(var name: String = "", var emoji: String = "")

fun Route.scopeFunctionRoutes() {
    get("/scope-functions") {
        // let:   `it` is the receiver; returns block result. Good for nullable chains.
        val withLet = "kotlin".let { name -> "Hello, $name!" }

        // run:   `this` is the receiver; returns block result. Like let, but receiver-style.
        val withRun = "kotlin".run { "Hello, $this!" }

        // with:  `this` is the receiver (passed as arg); returns block result. Not an extension fn.
        val withWith = with("kotlin") { "Hello, $this!" }

        // apply: `this` is the receiver; returns the receiver. Used to configure a mutable object.
        val withApply = GreetingBuilder().apply {
            name = "kotlin"
            emoji = "K"
        }

        // also:  `it` is the receiver; returns the receiver. Good for side effects (logging, etc.).
        val withAlso = "kotlin".also { println("logging: $it") }

        call.respond(
            mapOf(
                "let_returns_block_result" to withLet,
                "run_returns_block_result" to withRun,
                "with_returns_block_result" to withWith,
                "apply_returns_receiver" to withApply,
                "also_returns_receiver" to withAlso,
            )
        )
    }
}
