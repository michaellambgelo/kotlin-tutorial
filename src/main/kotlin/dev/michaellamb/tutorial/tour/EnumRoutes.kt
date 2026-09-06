package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: enum classes.
// An enum is a closed set of named instances, so each constant can carry its own
// properties and even override a member function. Because the set is closed, `when` over
// an enum is exhaustive without an `else` — add a constant and the compiler points at
// every `when` that no longer covers it. `entries` (Kotlin 1.9+) replaces `values()`.
private enum class Planet(val radiusKm: Double, val gravity: Double) {
    MERCURY(2_439.7, 3.7),
    EARTH(6_371.0, 9.81),
    JUPITER(69_911.0, 24.79) {
        // A constant may override behavior for itself alone.
        override fun blurb() = "${name.lowercase()} — big and stormy"
    };

    open fun blurb() = "${name.lowercase()} — radius ${radiusKm}km"

    fun weightOf(massKg: Double) = massKg * gravity
}

private fun advice(planet: Planet): String = when (planet) {
    Planet.MERCURY -> "bring sunscreen"
    Planet.EARTH -> "you are here"
    Planet.JUPITER -> "there is no ground"
    // No `else` — the compiler proves every constant is handled.
}

fun Route.enumRoutes() {
    get("/enums") {
        val requested = call.queryParameters["planet"]?.uppercase()
        // valueOf throws on an unknown name; entries.find is the total, null-returning form.
        val planet = Planet.entries.find { it.name == requested } ?: Planet.EARTH

        call.respond(
            mapOf(
                "selected" to planet.name,
                "ordinal" to planet.ordinal,
                "advice" to advice(planet),
                "weight_of_70kg" to planet.weightOf(70.0),
                "all_entries" to Planet.entries.map { it.name },
                "blurbs" to Planet.entries.associate { it.name to it.blurb() },
                "value_of_round_trip" to Planet.valueOf(planet.name).name,
            )
        )
    }
}
