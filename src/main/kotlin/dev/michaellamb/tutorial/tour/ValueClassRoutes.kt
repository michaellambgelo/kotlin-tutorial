package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: inline value classes.
// A `@JvmInline value class` wraps exactly one property and, wherever the compiler can,
// erases itself back to that property at runtime — the type safety costs no allocation.
// It buys you distinct types for values that are all Strings underneath, so passing an
// email where an id belongs stops compiling instead of failing in production.
@JvmInline
private value class UserId(val raw: String) {
    init {
        require(raw.isNotBlank()) { "id must not be blank" } // value classes can validate
    }
}

@JvmInline
private value class EmailAddress(val raw: String) {
    val domain: String get() = raw.substringAfter('@') // ...and expose computed properties
}

// The signature is now impossible to call with the two arguments swapped.
private fun invite(id: UserId, email: EmailAddress): String =
    "inviting ${id.raw} at ${email.raw}"

fun Route.valueClassRoutes() {
    get("/value-classes") {
        val id = UserId("u-1138")
        val email = EmailAddress("han@falcon.test")

        val rejected = runCatching { UserId("  ") }.exceptionOrNull()?.message

        call.respond(
            mapOf(
                "invite" to invite(id, email),
                // invite(email, id) would not compile — both are Strings at runtime, not to the compiler.
                "id_raw" to id.raw,
                "email_domain" to email.domain,
                "equality_is_by_value" to (EmailAddress("han@falcon.test") == email),
                "init_block_rejects_blank" to rejected,
            )
        )
    }
}
