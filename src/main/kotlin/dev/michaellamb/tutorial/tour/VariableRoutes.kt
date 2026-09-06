package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: variables and string templates.
// `val` declares a read-only reference (assign once); `var` is reassignable. Types are
// inferred from the initializer, so you only write them when there is nothing to infer
// from — a declaration without an initializer needs an explicit type. String templates
// interpolate a value with `$name`, or any expression with `${...}`.
fun Route.variableRoutes() {
    get("/variables") {
        val name = call.queryParameters["name"] ?: "Kotlin"

        val inferredInt = 42 // Int, inferred
        val declaredLong: Long = 42 // explicit — no Int-to-Long widening in Kotlin

        var counter = 0 // var: reassignable
        counter += 1
        counter += 1

        // Declared now, assigned later: the compiler cannot infer, so the type is required.
        val greeting: String
        greeting = "Hello, $name!"

        call.respond(
            mapOf(
                "greeting" to greeting,
                "template_with_expression" to "$name has ${name.length} characters",
                // A literal dollar sign has to be escaped in a template.
                "escaped_dollar" to "${'$'}$inferredInt",
                "inferred_type" to inferredInt::class.simpleName,
                "declared_type" to declaredLong::class.simpleName,
                "var_after_two_increments" to counter,
            )
        )
    }
}
