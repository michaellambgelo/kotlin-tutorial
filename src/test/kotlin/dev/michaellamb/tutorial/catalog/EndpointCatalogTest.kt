package dev.michaellamb.tutorial.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the invariants the catalog exists to hold. These are the checks that turn "adding a tour
 * route means editing three places" (CLAUDE.md) from a convention into a failure.
 */
class EndpointCatalogTest {

    @Test
    fun `every tour card appears in exactly one chapter`() {
        val carded = tourEndpoints.map { it.specPath }.toSet()
        val chaptered = tourChapters.values.flatten()

        assertEquals(
            chaptered.size,
            chaptered.toSet().size,
            "a route is listed in more than one chapter: $chaptered",
        )
        assertEquals(
            emptySet(),
            carded - chaptered.toSet(),
            "tour routes with a home-page card but no chapter — add them to tourChapters",
        )
        assertEquals(
            emptySet(),
            chaptered.toSet() - carded,
            "tourChapters lists routes that have no card — stale entries",
        )
    }

    @Test
    fun `operation ids are unique and URL-safe`() {
        val ids = (tourEndpoints + healthCard).map { operationIdFor(it.method, it.specPath) } +
            widgetCards.map { operationIdFor("GET", it.path) }

        assertEquals(ids.size, ids.toSet().size, "duplicate operationId in $ids")
        // swagger-ui encodeURIComponent's the anchor, so anything outside this set would silently
        // change the href the home page emits.
        val illegal = ids.filterNot { it.matches(Regex("[A-Za-z0-9_]+")) }
        assertEquals(emptyList(), illegal, "operationIds must be alphanumeric + underscore")
    }

    @Test
    fun `every tag used by a route is declared in the document-level tag order`() {
        val declared = tagOrder.map { it.first }.toSet()
        val used = (tourEndpoints + healthCard).map { tagFor(it.specPath) } +
            widgetCards.map { tagFor(it.path) } +
            endpointSummaries.keys.map { (_, path) -> tagFor(path) }

        assertEquals(
            emptySet(),
            used.toSet() - declared,
            "a route uses a tag missing from tagOrder, so Swagger would order it arbitrarily",
        )
    }

    @Test
    fun `card paths keep their demo query strings but spec paths do not`() {
        val withQuery = tourEndpoints.filter { it.path.contains('?') }
        assertTrue(withQuery.isNotEmpty(), "expected some cards to carry demo query strings")
        assertTrue(
            withQuery.none { it.specPath.contains('?') },
            "specPath must strip the query or the Swagger anchor will not resolve",
        )
    }

    @Test
    fun `every card has a non-blank summary and snippet`() {
        (tourEndpoints + healthCard).forEach { card ->
            assertTrue(card.summary.isNotBlank(), "${card.path} needs a summary — it feeds the spec")
            assertTrue(card.snippet.isNotBlank(), "${card.path} needs a source snippet")
        }
    }
}
