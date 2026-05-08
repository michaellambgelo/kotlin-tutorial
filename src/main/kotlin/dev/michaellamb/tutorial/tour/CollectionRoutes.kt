package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: collection operations.
// Standard library extensions on Iterable read like SQL: map, filter, groupBy, sumOf,
// windowed, zipWithNext, partition, fold, reduce. Composable and lazy via `asSequence()`.
private data class Book(val title: String, val author: String, val pages: Int)

private val library = listOf(
    Book("Project Hail Mary", "Andy Weir", 476),
    Book("The Martian", "Andy Weir", 384),
    Book("Dune", "Frank Herbert", 688),
    Book("Children of Dune", "Frank Herbert", 408),
    Book("Snow Crash", "Neal Stephenson", 470),
    Book("Anathem", "Neal Stephenson", 935),
)

fun Route.collectionRoutes() {
    get("/collections") {
        val byAuthor = library.groupBy { it.author }
        val totalPages = library.sumOf { it.pages }
        val longestBook = library.maxByOrNull { it.pages }
        val (long, short) = library.partition { it.pages > 500 }

        call.respond(
            mapOf(
                "books_by_author" to byAuthor.mapValues { (_, books) -> books.map { it.title } },
                "total_pages" to totalPages,
                "longest_book" to longestBook,
                "long_books" to long.map { it.title },
                "short_books" to short.map { it.title },
                "running_pages" to library.runningFold(0) { acc, b -> acc + b.pages },
            )
        )
    }
}
