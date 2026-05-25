package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: higher-order functions.
// Functions are values: store them in vals, pass them as arguments, return them. `then`
// composes two functions into one. `applyN` takes a function and applies it repeatedly.
// `String::uppercase` is a function reference — a method named, not called.
private val double: (Int) -> Int = { it * 2 }
private val increment: (Int) -> Int = { it + 1 }

// Compose: (f then g)(x) == g(f(x)). Generic over all three types.
private infix fun <A, B, C> ((A) -> B).then(g: (B) -> C): (A) -> C = { a -> g(this(a)) }

// A higher-order function: applies `f` to `start`, `n` times.
private fun applyN(n: Int, f: (Int) -> Int, start: Int): Int {
    var acc = start
    repeat(n) { acc = f(acc) }
    return acc
}

fun Route.higherOrderFunctionRoutes() {
    get("/higher-order-functions") {
        val x = call.queryParameters["x"]?.toIntOrNull() ?: 10
        val doubleThenInc = double then increment // build a new function

        call.respond(
            mapOf(
                "x" to x,
                "double(x)" to double(x),
                "double_then_increment(x)" to doubleThenInc(x), // (x*2)+1
                "apply_double_3x" to applyN(3, double, x), // x*2*2*2
                "uppercased_via_method_ref" to
                    listOf("luke", "leia", "han").map(String::uppercase),
            )
        )
    }
}
