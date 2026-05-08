package dev.michaellamb.tutorial.notes

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
// - Elvis with `return@get` — idiomatic 404 short-circuit on null
// - `require { }` — throws IllegalArgumentException; Ktor's StatusPages can map it
fun Route.noteRoutes(repo: NoteRepository) {
    route("/notes") {
        get {
            call.respond(repo.list())
        }

        get("/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
            val note = repo.get(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "note not found"))
            call.respond(note)
        }

        post {
            val req = call.receive<CreateNoteRequest>()
            if (req.title.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "title required"))
            }
            val created = repo.create(req)
            call.respond(HttpStatusCode.Created, created)
        }

        put("/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
            val req = call.receive<UpdateNoteRequest>()
            val updated = repo.update(id, req)
                ?: return@put call.respond(HttpStatusCode.NotFound, mapOf("error" to "note not found"))
            call.respond(updated)
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid id"))
            if (repo.delete(id)) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "note not found"))
            }
        }
    }
}
