package dev.michaellamb.tutorial.notes

import dev.michaellamb.tutorial.serialization.InstantAsStringSerializer
import dev.michaellamb.tutorial.serialization.UuidAsStringSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// @Serializable here drives OpenAPI schema generation only; Jackson handles runtime JSON.
@Serializable
data class Note(
    @Serializable(with = UuidAsStringSerializer::class) val id: UUID,
    val title: String,
    val body: String,
    @Serializable(with = InstantAsStringSerializer::class) val createdAt: Instant,
)

@Serializable
data class CreateNoteRequest(val title: String, val body: String)

@Serializable
data class UpdateNoteRequest(val title: String?, val body: String?)
