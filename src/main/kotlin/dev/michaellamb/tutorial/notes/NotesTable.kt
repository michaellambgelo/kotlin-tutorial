package dev.michaellamb.tutorial.notes

import org.jetbrains.exposed.sql.Table

// Exposed DSL table for /notes. `id` is the primary key (UUID -> BLOB in SQLite). `createdAt` is
// stored as epoch millis in a long column, NOT exposed-java-time's timestamp(): on SQLite that
// column type round-trips through the JVM default zone and shifts the Instant by the local UTC
// offset. Epoch millis is timezone-proof and sorts chronologically as a number (so ORDER BY DESC
// is correct). NoteRepository maps ResultRow <-> the Note data class by hand (Instant <-> millis),
// so Note + its Jackson/OpenAPI serialization stay intact. text() (not varchar) for title/body
// because they're unbounded — SQLite ignores column length anyway.
object NotesTable : Table("notes") {
    val id = uuid("id")
    val title = text("title")
    val body = text("body")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
