package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: the three collection types, read-only and mutable.
// `listOf` / `setOf` / `mapOf` build read-only views; the `mutableXOf` builders add the
// write half of the API. Read-only is the default you reach for: a `List` has no `add`,
// so passing one around cannot surprise you. A Set drops duplicates and ignores order;
// a Map is keyed, and `[key]` returns null for a miss rather than throwing.
fun Route.collectionTypeRoutes() {
    get("/collection-types") {
        val readOnly = listOf("green", "red", "blue")
        val mutable = mutableListOf("green", "red", "blue").apply {
            add("yellow")
            remove("red")
        }

        val set = setOf("a", "b", "a", "c") // duplicates collapse
        val map = mapOf("apple" to 100, "kiwi" to 190, "orange" to 100)

        call.respond(
            mapOf(
                "read_only_list" to readOnly,
                "mutable_list_after_add_and_remove" to mutable,
                "list_first_and_last" to listOf(readOnly.first(), readOnly.last()),
                "in_operator" to ("red" in readOnly),
                "set_drops_duplicates" to set,
                "set_size" to set.size, // 3, not 4
                "map_keys" to map.keys,
                "map_values" to map.values,
                "map_lookup_hit" to map["kiwi"],
                "map_lookup_miss_is_null" to map["durian"],
                "map_contains_key" to map.containsKey("apple"),
            )
        )
    }
}
