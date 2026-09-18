package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: typealias — a new NAME for an existing type, not a new type.
// This is the key distinction, and the contrast with /tour/value-classes is the lesson:
//
//   typealias UserId = String      // an alias. UserId IS String. Interchangeable everywhere.
//   value class UserId(val v: String)  // a new type. Passing a String where UserId is expected
//                                      // does not compile.
//
// So typealias buys readability, never safety. It earns its keep on structural types that are
// painful to read and impossible to name otherwise: function types, deeply nested generics, and
// disambiguating two imported classes with the same simple name.

// A function type given a name — the signature now reads as a concept.
private typealias Validator<T> = (T) -> String?

// A nested generic that would otherwise be restated at every use site.
private typealias RoutesByChapter = Map<String, List<String>>

// Disambiguating same-named imports — the only way to have both in one file.
private typealias JavaList<T> = java.util.ArrayList<T>

private val notBlank: Validator<String> = { if (it.isBlank()) "must not be blank" else null }
private val maxLen: Validator<String> = { if (it.length > 10) "too long (${it.length} > 10)" else null }

private fun validate(value: String, vararg rules: Validator<String>): List<String> =
    rules.mapNotNull { rule -> rule(value) }

fun Route.typeAliasRoutes() {
    get("/type-alias") {
        val chapters: RoutesByChapter = mapOf(
            "beginner" to listOf("/tour/variables", "/tour/basic-types"),
            "beyond" to listOf("/tour/flow", "/tour/operators"),
        )

        val javaBacked: JavaList<String> = JavaList()
        javaBacked.add("built through an alias")

        // The proof that an alias is not a new type: a Validator<String> IS a (String) -> String?,
        // so a plain lambda is accepted where the alias is declared, and vice versa.
        val inlineLambda: (String) -> String? = notBlank

        call.respond(
            mapOf(
                "validation_of_blank" to validate("   ", notBlank, maxLen),
                "validation_of_long" to validate("a".repeat(20), notBlank, maxLen),
                "validation_of_ok" to validate("kodee", notBlank, maxLen),
                "chapters" to chapters,
                "java_backed" to javaBacked,
                "alias_is_the_same_type" to (inlineLambda === notBlank), // true — no wrapper exists
                "alias_vs_value_class" to mapOf(
                    "typealias" to "a name. No new type, no safety, zero runtime cost, fully interchangeable.",
                    "value class" to "a real type. Catches mix-ups at compile time; see /tour/value-classes.",
                ),
            ),
        )
    }
}
