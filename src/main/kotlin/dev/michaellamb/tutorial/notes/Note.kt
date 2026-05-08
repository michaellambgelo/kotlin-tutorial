package dev.michaellamb.tutorial.notes

import java.time.Instant
import java.util.UUID

data class Note(
    val id: UUID,
    val title: String,
    val body: String,
    val createdAt: Instant,
)

data class CreateNoteRequest(val title: String, val body: String)

data class UpdateNoteRequest(val title: String?, val body: String?)
