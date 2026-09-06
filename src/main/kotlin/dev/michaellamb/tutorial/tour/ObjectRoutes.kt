package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: object declarations.
// `object` declares a class and its single instance at once — a singleton, created lazily
// and thread-safely on first access, with no constructor. `data object` adds the derived
// toString()/equals() (but not copy(): there is nothing to copy). A `companion object`
// belongs to its class, so its members are reached through the class name and act as the
// static side that Kotlin classes otherwise don't have.
private object Registry {
    private val entries = mutableListOf<String>()

    fun register(name: String): Int {
        entries.add(name)
        return entries.size
    }
}

private data object AppConfig {
    const val APP_NAME = "kotlin-tutorial"
    const val VERSION = "1.0.0"
}

private class Temperature private constructor(val celsius: Double) {
    // Factory functions on the companion replace the secondary constructors you'd write in Java.
    companion object Factory {
        fun fromFahrenheit(f: Double) = Temperature((f - 32) * 5 / 9)
        fun fromCelsius(c: Double) = Temperature(c)
    }
}

private interface Sorter {
    fun sort(input: List<Int>): List<Int>
}

fun Route.objectRoutes() {
    get("/objects") {
        // An object *expression* — an anonymous one-off implementation, no name, no singleton.
        val descending = object : Sorter {
            override fun sort(input: List<Int>) = input.sortedDescending()
        }

        call.respond(
            mapOf(
                // The singleton is created once for the whole process, so its state survives
                // between requests — call this route twice and the counters keep climbing.
                "registry_count_after_first_register" to Registry.register("alpha"),
                "registry_count_after_second_register" to Registry.register("beta"),
                // data object: toString() is derived from the name...
                "data_object_to_string" to AppConfig.toString(),
                "data_object_app_name" to AppConfig.APP_NAME,
                "data_object_version" to AppConfig.VERSION,
                // ...where a plain object falls back to the identity-hash default.
                "plain_object_to_string_is_default" to Registry.toString().contains("@"),
                "companion_from_fahrenheit" to Temperature.fromFahrenheit(212.0).celsius,
                "companion_from_celsius" to Temperature.fromCelsius(21.5).celsius,
                "object_expression_sort" to descending.sort(listOf(3, 1, 4, 1, 5)),
            )
        )
    }
}
