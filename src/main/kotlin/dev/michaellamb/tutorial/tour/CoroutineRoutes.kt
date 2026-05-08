package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.system.measureTimeMillis

// Kotlin: structured concurrency.
// `suspend` functions can be paused without blocking the OS thread. `coroutineScope { async { ... } }`
// launches concurrent children whose lifetimes are tied to the scope — if any throws, all are cancelled.
private suspend fun fakeApiCall(label: String, delayMs: Long): String {
    delay(delayMs)
    return "$label finished after ${delayMs}ms"
}

fun Route.coroutineRoutes() {
    get("/coroutines") {
        val sequentialMs = measureTimeMillis {
            fakeApiCall("a", 200)
            fakeApiCall("b", 200)
            fakeApiCall("c", 200)
        }

        val results: List<String>
        val parallelMs = measureTimeMillis {
            results = coroutineScope {
                val a = async { fakeApiCall("a", 200) }
                val b = async { fakeApiCall("b", 200) }
                val c = async { fakeApiCall("c", 200) }
                listOf(a.await(), b.await(), c.await())
            }
        }

        call.respond(
            mapOf(
                "sequential_ms" to sequentialMs,
                "parallel_ms" to parallelMs,
                "results" to results,
                "speedup" to "%.2fx".format(sequentialMs.toDouble() / parallelMs),
            )
        )
    }
}
