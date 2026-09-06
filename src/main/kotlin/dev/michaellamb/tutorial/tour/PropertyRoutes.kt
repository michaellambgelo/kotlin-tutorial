package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.properties.Delegates

// Kotlin: properties beyond the plain `val`.
// A property is a getter (and for `var`, a setter) over a hidden **backing field** that
// you reach with `field` — assigning to the property's own name inside its setter would
// recurse forever. Extension properties have no backing field at all, so they must define
// a get(). And `by` delegates a property's accessors to another object: `lazy` computes
// once on first read, `Delegates.observable` fires a callback on every write.
private class Person {
    // Custom setter, normalizing through the backing field.
    var name: String = ""
        set(value) {
            field = value.trim().replaceFirstChar { it.uppercase() }
        }

    // No backing field: computed on every read.
    val initial: Char? get() = name.firstOrNull()
}

// An extension property — a getter bolted onto a type you don't own.
private val String.lastChar: Char get() = this[length - 1]

private class Config {
    val changes = mutableListOf<String>()
    var tokenComputations = 0
        private set

    // Delegated: computed on first access only. The counter proves it runs once.
    val expensiveToken: String by lazy {
        tokenComputations++
        "token-$tokenComputations"
    }

    // Delegated: every assignment goes through the handler.
    var logLevel: String by Delegates.observable("INFO") { property, old, new ->
        changes.add("${property.name}: $old -> $new")
    }
}

fun Route.propertyRoutes() {
    get("/properties") {
        val person = Person().apply { name = "  kodee " }

        val config = Config()
        val firstRead = config.expensiveToken
        val secondRead = config.expensiveToken // no recompute — `lazy` caches
        config.logLevel = "DEBUG"
        config.logLevel = "TRACE"

        call.respond(
            mapOf(
                "backing_field_normalized_name" to person.name, // "Kodee"
                "computed_property_initial" to person.initial?.toString(),
                "extension_property_last_char" to "Kotlin".lastChar.toString(),
                "lazy_first_read" to firstRead,
                "lazy_second_read_is_cached" to (firstRead === secondRead),
                "lazy_block_ran_once" to config.tokenComputations,
                "observable_change_log" to config.changes,
                "observable_current_value" to config.logLevel,
            )
        )
    }
}
