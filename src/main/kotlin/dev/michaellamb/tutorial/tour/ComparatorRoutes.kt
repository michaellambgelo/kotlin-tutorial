package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: ordering — Comparable, Comparator, and the compareBy family.
// Two different jobs, easily confused:
//   Comparable  — "this type has ONE natural order", implemented once on the class (compareTo).
//   Comparator  — "here is AN order", a separate object, so a type can have many.
//
// The stdlib builders compose comparators out of key selectors, which is why you almost never write
// a subtraction-based compare by hand: `compareBy { }` then `.thenBy { }`, with `reversed()` and
// `nullsFirst()`/`nullsLast()` as wrappers. Sorting is stable, so `thenBy` genuinely breaks ties
// rather than re-shuffling equals.
private data class Film(val title: String, val year: Int, val rating: Double?)

private val films = listOf(
    Film("Dune: Part Two", 2024, 5.0),
    Film("Arrival", 2016, 4.5),
    Film("Sicario", 2015, 4.5),
    Film("Blade Runner 2049", 2017, 5.0),
    Film("Enemy", 2013, null), // unrated — the null the comparators have to place
)

fun Route.comparatorRoutes() {
    get("/comparators") {
        val byTitle = films.sortedBy { it.title }

        // Compose: highest rating first, then oldest first within a rating.
        val byRatingThenYear = films.sortedWith(
            compareByDescending<Film> { it.rating ?: -1.0 }.thenBy { it.year },
        )

        // Placing nulls explicitly rather than coercing them to a sentinel.
        val nullsLast = films.sortedWith(compareBy(nullsLast()) { it.rating })
        val nullsFirst = films.sortedWith(compareBy(nullsFirst()) { it.rating })

        // Stability: sorting only by rating must preserve the original relative order of the ties.
        val stable = films.filter { it.rating == 4.5 }.sortedBy { it.rating }.map { it.title }

        // minOf/maxOf/sortedWith all accept a Comparator, so one comparator serves every operation.
        val newestFirst = compareByDescending<Film> { it.year }

        call.respond(
            mapOf(
                "by_title" to byTitle.map { it.title },
                "by_rating_then_year" to byRatingThenYear.map { "${it.title} (${it.year}) ${it.rating ?: "—"}" },
                "nulls_last" to nullsLast.map { it.title },
                "nulls_first" to nullsFirst.map { it.title },
                "stable_ties_keep_input_order" to stable, // Arrival before Sicario, as declared
                "newest" to films.minWith(newestFirst).title,
                "oldest" to films.maxWith(newestFirst).title,
                "reversed_natural_order" to films.map { it.year }.sortedDescending(),
                "comparable_vs_comparator" to mapOf(
                    "Comparable" to "one natural order, on the type itself (Int, String, and Money in /tour/operators)",
                    "Comparator" to "any number of orders, built outside the type with compareBy/thenBy",
                ),
            ),
        )
    }
}
