package dev.michaellamb.tutorial.notes

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class NoteRepository {
    private val store = ConcurrentHashMap<UUID, Note>()

    fun list(): List<Note> = store.values.sortedByDescending { it.createdAt }

    fun get(id: UUID): Note? = store[id]

    fun create(req: CreateNoteRequest): Note {
        val note = Note(
            id = UUID.randomUUID(),
            title = req.title,
            body = req.body,
            createdAt = Instant.now(),
        )
        store[note.id] = note
        return note
    }

    fun update(id: UUID, req: UpdateNoteRequest): Note? {
        val existing = store[id] ?: return null
        val updated = existing.copy(
            title = req.title ?: existing.title,
            body = req.body ?: existing.body,
        )
        store[id] = updated
        return updated
    }

    fun delete(id: UUID): Boolean = store.remove(id) != null
}
