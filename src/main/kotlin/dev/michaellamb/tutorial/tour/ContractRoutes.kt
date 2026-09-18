package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// Kotlin: contracts — how the compiler learns what YOUR function proved.
// /tour/smart-casts shows the compiler narrowing a type after `if (x is String)`. That works because
// the check is right there. Move the same check into a function and the knowledge is lost:
//
//     if (isNotNullString(x)) { x.length }   // ERROR — the compiler cannot see inside
//
// A contract is how a function tells the compiler what a `true` return implies. It is why the
// stdlib's own `isNullOrBlank()` smart-casts at the call site and a hand-rolled copy does not —
// there is nothing magic about the stdlib, it just declares a contract.
//
// The API is still experimental (hence @OptIn — see /tour/opt-in), and it is a PROMISE, not a proof:
// the compiler trusts the declaration without verifying it. Lie in a contract and you get exactly
// the unsoundness you asked for.

@OptIn(ExperimentalContracts::class)
private fun isPresent(value: String?): Boolean {
    contract {
        // "if this returns true, the argument was not null"
        returns(true) implies (value != null)
    }
    return !value.isNullOrBlank()
}

// The second kind of contract: how often a lambda runs. `callsInPlace` with EXACTLY_ONCE is what
// allows a `val` to be assigned inside a lambda and still be definitely-initialised afterwards.
@OptIn(ExperimentalContracts::class)
private inline fun <T> measured(block: () -> T): Pair<T, Long> {
    contract { callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE) }
    val start = System.nanoTime()
    val result = block()
    return result to (System.nanoTime() - start)
}

fun Route.contractRoutes() {
    get("/contracts") {
        val maybe: String? = "kodee"

        val lengthAfterContract = if (isPresent(maybe)) {
            // Smart-cast to String purely because of the contract on isPresent.
            maybe.length
        } else {
            0
        }

        // callsInPlace lets this `val` be assigned inside the lambda without the compiler
        // complaining that it might be assigned twice or not at all.
        val label: String
        val (sum, nanos) = measured {
            label = "sum of 1..1000"
            (1..1000).sum()
        }

        call.respond(
            mapOf(
                "input" to maybe,
                "length_via_contract_smart_cast" to lengthAfterContract,
                "stdlib_equivalent" to "isNullOrBlank() declares this same contract — that is why it smart-casts",
                "measured_label" to label,
                "measured_result" to sum,
                "measured_took_nanos" to nanos,
                "caveat" to "a contract is trusted, not verified — the compiler believes whatever you declare",
                "contract_kinds" to listOf(
                    "returns(true) implies (x != null) — narrow a type from a boolean result",
                    "returnsNotNull() implies (...) — same, for a nullable return",
                    "callsInPlace(block, EXACTLY_ONCE) — enable definite assignment across a lambda",
                ),
            ),
        )
    }
}
