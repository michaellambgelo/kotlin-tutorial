package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: function declarations.
// Parameters can have default values, so one function replaces a pile of overloads, and
// callers can name arguments to pass them in any order (and to make the call site read).
// A function whose body is a single expression drops the braces and the return type.
private fun greet(
    name: String,
    greeting: String = "Hello",
    excited: Boolean = false,
): String = "$greeting, $name${if (excited) "!" else "."}"

// Single-expression function: the return type is inferred from the expression.
private fun square(n: Int) = n * n

// Early return: guard clauses exit before the main body runs.
private fun initials(fullName: String): String {
    if (fullName.isBlank()) return "??"
    return fullName.trim().split(Regex("\\s+")).joinToString("") { it.first().uppercase() }
}

// No `return` type written means `Unit` — Kotlin's "returns nothing useful".
private fun logIt(message: String) {
    println("log: $message")
}

fun Route.functionRoutes() {
    get("/functions") {
        val name = call.queryParameters["name"] ?: "Kodee"
        logIt("greeting $name")

        call.respond(
            mapOf(
                "all_defaults" to greet(name),
                // Named arguments: skip the middle parameter, reorder the rest freely.
                "named_argument_skips_middle" to greet(name, excited = true),
                "named_arguments_reordered" to greet(greeting = "Howdy", name = name),
                "single_expression_square" to square(9),
                "early_return_initials" to initials(name),
                "early_return_on_blank" to initials("   "),
                "unit_is_a_real_type" to logIt("side effect").let { "kotlin.Unit" },
            )
        )
    }
}
