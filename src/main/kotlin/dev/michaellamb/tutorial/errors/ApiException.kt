/*
 * Error model shared by the whole service.
 *
 * Pedagogical focus: a **sealed hierarchy carrying behaviour**. `/tour/sealed-when` demonstrates
 * `sealed` on a toy `Shape`; this is the same feature doing load-bearing work. Because ApiException
 * is sealed, the `when` in plugins/StatusPages.kt that maps a failure to an HTTP status needs no
 * `else` branch — and adding a subclass here breaks that build until it is handled. That guarantee
 * is the entire reason to reach for `sealed` over a plain open class.
 *
 * Note these are exceptions, not return values. /tour/result covers the other half of the tradeoff:
 * `Result<T>` when the caller should decide, a thrown exception when the edge of the app should.
 */
package dev.michaellamb.tutorial.errors

import kotlinx.serialization.Serializable

sealed class ApiException(message: String) : RuntimeException(message) {
    /** The request itself was malformed — an unparseable id, a missing field. */
    class BadRequest(message: String) : ApiException(message)

    /** The route was valid but the addressed row does not exist. */
    class NotFound(resource: String, id: Any) : ApiException("$resource $id not found")

    /** The request was well-formed but conflicts with current state. */
    class Conflict(message: String) : ApiException(message)
}

// @Serializable drives OpenAPI schema generation only; Jackson handles the runtime JSON.
// Replaces the ad-hoc `mapOf("error" to ...)` bodies the note routes used to build by hand, so the
// error shape is now one type the spec can actually describe.
@Serializable
data class ErrorResponse(val error: String)
