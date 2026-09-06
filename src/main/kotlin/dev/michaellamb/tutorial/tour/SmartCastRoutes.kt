package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: smart casts and safe casts.
// Once `is` proves a type (or `!is` returns early), the compiler smart-casts the value —
// no redundant cast to write. `as` is the unsafe cast that throws on a mismatch; `as?`
// returns null instead, which pairs naturally with `?:`. Collections have their own
// null-aware tools: `filterNotNull` and the nullable-aware `mapNotNull`.
private fun describe(input: Any): String = when (input) {
    // Smart cast inside each branch: `input` is already the branch's type.
    is Int -> "Int doubled to ${input * 2}"
    is String -> "String of ${input.length} chars, upper: ${input.uppercase()}"
    is List<*> -> "List of ${input.size} items"
    else -> "unhandled ${input::class.simpleName}"
}

// Elvis with an early return: the guard reads as one line instead of an if/else block.
private fun domainOf(email: String?): String {
    val at = email?.indexOf('@') ?: return "no email"
    if (at < 0) return "not an email"
    return email.substring(at + 1) // smart cast: email is non-null past the Elvis return
}

fun Route.smartCastRoutes() {
    get("/smart-casts") {
        val anything: Any = call.queryParameters["value"] ?: 21

        val mixed: List<Any> = listOf(1, "two", listOf(3, 4), 5.0)
        val withNulls: List<String?> = listOf("alpha", null, "beta", null)

        // as? — a cast that yields null instead of throwing, with an Elvis fallback.
        val safeCast = (anything as? String)?.uppercase() ?: "not a String"
        val unsafeCastFailed = runCatching { anything as List<*> }.isFailure

        call.respond(
            mapOf(
                "input" to anything,
                "described" to describe(anything),
                "described_each" to mixed.map { describe(it) },
                "is_not_check" to (anything !is Double),
                "safe_cast_or_fallback" to safeCast,
                "unsafe_cast_throws" to unsafeCastFailed,
                "filter_not_null" to withNulls.filterNotNull(),
                "map_not_null_lengths" to withNulls.mapNotNull { it?.length },
                "elvis_early_return" to listOf(domainOf("han@falcon.test"), domainOf(null), domainOf("nope")),
            )
        )
    }
}
