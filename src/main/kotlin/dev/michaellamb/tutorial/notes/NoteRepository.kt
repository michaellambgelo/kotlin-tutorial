package dev.michaellamb.tutorial.notes

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// Exposed DSL repository backing /notes. Each method is a suspended transaction on Dispatchers.IO,
// so the blocking SQLite JDBC calls run off Netty's event-loop threads. Same public surface as the
// old in-memory ConcurrentHashMap version (just `suspend` now), so NoteRoutes.kt is unchanged — its
// handlers are already suspend.
class NoteRepository(private val db: Database) {

    private fun ResultRow.toNote() = Note(
        id = this[NotesTable.id],
        title = this[NotesTable.title],
        body = this[NotesTable.body],
        createdAt = Instant.ofEpochMilli(this[NotesTable.createdAt]),
    )

    suspend fun list(): List<Note> = newSuspendedTransaction(Dispatchers.IO, db) {
        NotesTable.selectAll()
            .orderBy(NotesTable.createdAt to SortOrder.DESC)
            .map { it.toNote() }
    }

    suspend fun get(id: UUID): Note? = newSuspendedTransaction(Dispatchers.IO, db) {
        NotesTable.selectAll().where { NotesTable.id eq id }.singleOrNull()?.toNote()
    }

    suspend fun create(req: CreateNoteRequest): Note = newSuspendedTransaction(Dispatchers.IO, db) {
        // Truncate to millis (our storage granularity) so the POST response equals later reads.
        val note = Note(
            id = UUID.randomUUID(),
            title = req.title,
            body = req.body,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )
        NotesTable.insert {
            it[id] = note.id
            it[title] = note.title
            it[body] = note.body
            it[createdAt] = note.createdAt.toEpochMilli()
        }
        note
    }

    suspend fun update(id: UUID, req: UpdateNoteRequest): Note? = newSuspendedTransaction(Dispatchers.IO, db) {
        val existing = NotesTable.selectAll().where { NotesTable.id eq id }.singleOrNull()?.toNote()
            ?: return@newSuspendedTransaction null
        val updated = existing.copy(
            title = req.title ?: existing.title,
            body = req.body ?: existing.body,
        )
        NotesTable.update({ NotesTable.id eq id }) {
            it[title] = updated.title
            it[body] = updated.body
        }
        updated
    }

    suspend fun delete(id: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO, db) {
        NotesTable.deleteWhere { NotesTable.id eq id } > 0
    }
}
