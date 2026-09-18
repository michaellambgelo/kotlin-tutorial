package dev.michaellamb.tutorial.notes

import dev.michaellamb.tutorial.errors.ApiException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.util.UUID

// Kotlin in a REST handler:
// - call.receive<T>() — Ktor uses ContentNegotiation to decode the JSON body to T
// - call.respond(status, body) — serialize and send
// - Elvis throwing instead of returning — `?: throw` is an expression, so the happy path stays
//   unindented and `id` is non-null below it. Compare the earlier version of this file, which
//   repeated `?: return@get call.respond(BadRequest, mapOf(...))` at four call sites.
// - Errors become plugins/StatusPages.kt's problem: handlers describe *what went wrong*, one
//   place decides *what that means over HTTP*.
fun Route.noteRoutes(repo: NoteRepository) {
    route("/notes") {
        get {
            call.respond(repo.list())
        }

        get("/{id}") {
            val id = call.noteId()
            val note = repo.get(id) ?: throw ApiException.NotFound("note", id)
            call.respond(note)
        }

        post {
            val req = call.receive<CreateNoteRequest>()
            // require() throws IllegalArgumentException, which StatusPages maps to a 400.
            require(req.title.isNotBlank()) { "title required" }
            call.respond(HttpStatusCode.Created, repo.create(req))
        }

        put("/{id}") {
            val id = call.noteId()
            val req = call.receive<UpdateNoteRequest>()
            val updated = repo.update(id, req) ?: throw ApiException.NotFound("note", id)
            call.respond(updated)
        }

        delete("/{id}") {
            val id = call.noteId()
            if (!repo.delete(id)) throw ApiException.NotFound("note", id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/**
 * The `{id}` path segment as a UUID.
 *
 * An extension function on ApplicationCall (see /tour/extensions) — the parsing that used to be
 * copy-pasted into four handlers, named once. Returns a non-null UUID or throws; there is no
 * "maybe" state for callers to forget about.
 */
private fun io.ktor.server.application.ApplicationCall.noteId(): UUID {
    val raw = parameters["id"] ?: throw ApiException.BadRequest("id required")
    return runCatching { UUID.fromString(raw) }
        .getOrElse { throw ApiException.BadRequest("invalid id: $raw") }
}
