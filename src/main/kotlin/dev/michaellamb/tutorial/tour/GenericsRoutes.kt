package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: generics.
// One `maxOf` works for any `Comparable` type via an upper bound. `Box<out T>` is a
// covariant producer (you can read T out, never write it in). `reified` keeps the type
// argument at runtime, so `filterIsInstance<T>()` can test against it — normally erased.
private fun <T : Comparable<T>> maxOf(items: List<T>): T? = items.maxOrNull()

private class Box<out T>(val value: T) // covariant: Box<Int> is usable as Box<Any>

private inline fun <reified T> List<*>.ofType(): List<T> = filterIsInstance<T>()

fun Route.genericsRoutes() {
    get("/generics") {
        val numbers = listOf(3, 41, 17, 8)
        val words = listOf("dune", "anathem", "snow crash")
        val mixed: List<Any> = listOf(1, "two", 3, "four", 5.0)

        call.respond(
            mapOf(
                "max_of_ints" to maxOf(numbers), // same generic fn, Int bound
                "max_of_strings" to maxOf(words), // ...and String bound
                "box_value" to Box("covariant").value,
                "only_ints_from_mixed" to mixed.ofType<Int>(), // reified at runtime
                "only_strings_from_mixed" to mixed.ofType<String>(),
            )
        )
    }
}
