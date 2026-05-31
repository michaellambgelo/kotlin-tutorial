package dev.michaellamb.tutorial.tour

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

// Kotlin: nullable types.
// `String?` is a different type from `String` — you cannot dereference it without proving
// non-null first. The compiler enforces this at compile time, eliminating most NPEs.
@Serializable
data class NullSafetyRequest(val nickname: String?)

fun Route.nullSafetyRoutes() {
    post("/null-safety") {
        val req = call.receive<NullSafetyRequest>()
        val nickname: String? = req.nickname

        // Safe call ?. — returns null if receiver is null
        val length = nickname?.length

        // Elvis ?: — fallback if left side is null
        val display = nickname ?: "anonymous"

        // let — runs block only if non-null
        val shouted = nickname?.let { it.uppercase() }

        // Smart cast — inside the if-block, the compiler knows nickname is non-null
        val firstChar = if (nickname != null) nickname[0].toString() else null

        call.respond(
            mapOf(
                "input" to nickname,
                "safe_call_length" to length,
                "elvis_display" to display,
                "let_uppercase" to shouted,
                "smart_cast_first_char" to firstChar,
            )
        )
    }
}
