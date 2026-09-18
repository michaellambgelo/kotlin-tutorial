package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.delay

// Kotlin: Flow — a sequence whose producer is allowed to suspend.
// /tour/sequences shows lazy pull: a Sequence computes the next element on demand, but it cannot
// `delay` or await a network call. A Flow can, which is the whole reason it exists.
//
// Flows are COLD: the `flow { }` block does not run until someone calls a terminal operator
// (collect/toList/first/fold), and it runs again, from scratch, for every collector. That is the
// opposite of a hot stream such as SharedFlow, where one producer fans out to many subscribers.
// Cold means a Flow is a *recipe*, so it is safe to store one in a `val` and hand it to anybody.
//
// Backpressure is structural rather than negotiated: `emit` is a suspending call, so a slow
// collector simply suspends the producer. Nothing buffers unless you ask for it.
//
// See stream/DigestStream.kt for the same idea serving live data over SSE.
fun Route.flowRoutes() {
    get("/flow") {
        // Cold: this counter proves the builder re-runs per collector.
        var builderRuns = 0
        val numbers = flow {
            builderRuns++
            for (i in 1..5) {
                delay(1) // legal here, impossible in a Sequence
                emit(i)
            }
        }

        val firstCollection = numbers.toList()
        val secondCollection = numbers.map { it * 10 }.toList()

        // Operators are just suspend functions over the stream; they run in the collector's context.
        val emissionOrder = mutableListOf<String>()
        val evens = flowOf(1, 2, 3, 4, 5, 6)
            .onEach { emissionOrder += "saw $it" }
            .filter { it % 2 == 0 }
            .map { it * it }
            .toList()

        val runningTotal = flowOf(1, 2, 3, 4).fold(0) { acc, n -> acc + n }
        val oddCount = flowOf(1, 2, 3, 4, 5).filter { it % 2 == 1 }.count()

        call.respond(
            mapOf(
                "first_collection" to firstCollection,
                "second_collection" to secondCollection,
                // 2, not 1 — each terminal operator re-ran the producer. This is what "cold" means.
                "builder_runs_after_two_collections" to builderRuns,
                "interleaved_not_batched" to emissionOrder, // operators run per element, in order
                "even_squares" to evens,
                "folded_total" to runningTotal,
                "odd_count" to oddCount,
                "hot_vs_cold" to mapOf(
                    "cold" to "flow { } — one execution per collector, starts on collect",
                    "hot" to "SharedFlow/StateFlow — one execution, many subscribers, runs regardless",
                    "sharing_strategy_example" to SharingStarted.WhileSubscribed().toString(),
                ),
            ),
        )
    }
}
