package dev.michaellamb.tutorial.projects

import org.jetbrains.exposed.sql.Table

// Exposed DSL table for the curated /about projects. Mirrors NotesTable's conventions: `id` is the
// UUID primary key, text() (not varchar) for unbounded strings, and `created_at` is epoch millis in
// a long column — NOT exposed-java-time's timestamp(), which shifts the Instant by the local UTC
// offset on SQLite round-trips. ProjectRepository maps ResultRow <-> the Project data class by hand.
//
// `tech` is a comma-separated text column (split/join in the repository) — a deliberately simple
// scalar encoding for a tiny list; no json column type or extra dependency. `status_monitor_id` is
// the nullable Uptime Kuma monitor id. `position` orders the list on the page (ascending).
object ProjectsTable : Table("projects") {
    val id = uuid("id")
    val name = text("name")
    val url = text("url")
    val description = text("description")
    val statusMonitorId = integer("status_monitor_id").nullable()
    val tech = text("tech")
    val archived = bool("archived")
    val position = integer("position")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
