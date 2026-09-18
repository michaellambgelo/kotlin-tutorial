/*
 * Centralised error mapping (Ktor's StatusPages plugin).
 *
 * Pedagogical focus: an **exhaustive `when` over a sealed hierarchy**. Handlers throw a domain
 * error; this is the single place that decides what that means over HTTP. The `when` below has no
 * `else` branch — the compiler proves it covers every ApiException subtype, so adding one is a
 * compile error here rather than a silently mis-statused response in production.
 *
 * Before this existed the note routes hand-built `call.respond(BadRequest, mapOf("error" to ...))`
 * at four separate call sites.
 */
package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.errors.ApiException
import dev.michaellamb.tutorial.errors.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            // Exhaustive: no `else`. Add an ApiException subtype and this stops compiling.
            val status = when (cause) {
                is ApiException.BadRequest -> HttpStatusCode.BadRequest
                is ApiException.NotFound -> HttpStatusCode.NotFound
                is ApiException.Conflict -> HttpStatusCode.Conflict
            }
            call.respond(status, ErrorResponse(cause.message ?: "request failed"))
        }

        // `require(...)` throws IllegalArgumentException; Ktor throws BadRequestException when
        // ContentNegotiation cannot decode the body. Both are client mistakes, not server faults.
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "invalid request"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "malformed request body"))
        }

        // Anything unmapped is a bug: log it with the stack trace, tell the client nothing useful.
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception for ${call.request.local.uri}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal error"))
        }
    }
}
