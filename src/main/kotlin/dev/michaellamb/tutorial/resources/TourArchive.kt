/*
 * Type-safe routing with Ktor's Resources plugin.
 *
 * Pedagogical focus: **annotations + kotlinx.serialization + reified generics, working together**.
 * Everywhere else in this service a route is a string and its parameters are stringly-typed:
 *
 *     get("/notes/{id}") { val id = call.parameters["id"] ... }   // typo compiles; missing param compiles
 *
 * With Resources the route *is* a class. `@Resource("/archive")` makes `Archive` describe a path,
 * `@Serializable` lets Ktor decode the query string into its constructor, and `get<Archive> { }`
 * uses a reified type parameter (see /tour/generics) to know which class to build. The handler
 * receives a real object: `archive.year` is an `Int?`, checked at compile time, and the URL is
 * built from the same class by `href()` — so a link and its handler can never disagree.
 *
 * The cost: the route tree is no longer greppable as strings, and every resource needs both
 * annotations. That's the trade — this file exists to show it, not to replace the string routes.
 */
package dev.michaellamb.tutorial.resources

import dev.michaellamb.tutorial.catalog.tourChapters
import io.ktor.resources.Resource
import io.ktor.server.resources.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

/**
 * `GET /archive?year=&tag=` — a filterable index of the tour routes.
 *
 * Nested classes model nested paths: `Archive.Chapter` renders as `/archive/{name}` and holds a
 * reference to its parent, so the parent's query parameters remain available.
 */
@Serializable
@Resource("/archive")
class Archive(val year: Int? = null, val tag: String? = null) {

    @Serializable
    @Resource("{name}")
    class Chapter(val parent: Archive = Archive(), val name: String)
}

/**
 * The chapter index, owned by the catalog so it cannot drift from the mounted routes.
 * See catalog/EndpointCatalog.kt — a test asserts it covers exactly the tour cards.
 */
private val chapters: Map<String, List<String>> get() = tourChapters

fun Route.tourArchiveRoutes() {
    // `archive` arrives fully typed — no call.parameters lookups, no manual toIntOrNull().
    get<Archive> { archive ->
        val filtered = when (val tag = archive.tag) {
            null -> chapters
            else -> chapters.filterKeys { it == tag }
        }
        call.respond(
            mapOf<String, Any?>(
                "filter" to mapOf("year" to archive.year, "tag" to archive.tag),
                "chapters" to filtered,
            ),
        )
    }

    get<Archive.Chapter> { chapter ->
        val routes = chapters[chapter.name]
        call.respond(
            mapOf<String, Any?>(
                "chapter" to chapter.name,
                // The parent resource's query params are still reachable through `parent`.
                "inheritedTag" to chapter.parent.tag,
                "routes" to (routes ?: emptyList()),
                "known" to (routes != null),
            ),
        )
    }
}
