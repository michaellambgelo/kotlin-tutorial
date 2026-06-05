package dev.michaellamb.tutorial.projects

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.File

// Owns the SQLite connection + schema for /admin/projects, in its own file (projects.db) alongside
// notes.db on the same mounted volume. A separate database — rather than a second table inside
// notes.db — keeps this feature a faithful copy of the notes persistence pattern (own table, own
// Database, own env-overridable path), which is the point of this pedagogical repo. connect() is
// called once per Application from plugins/Routing.kt.
object ProjectsDatabase {
    // Default under build/ (gitignored) so dev runs never commit a DB. The container overrides via
    // PROJECTS_DB_PATH=/app/data/projects.db (Dockerfile ENV; the volume mounts /app/data). Tests
    // override via the projects.db.path system property for an isolated DB per testApplication.
    private const val DEFAULT_PATH = "build/projects.db"

    fun connect(path: String = resolvePath()): Database {
        File(path).absoluteFile.parentFile?.mkdirs()
        // WAL + busy_timeout on the DataSource (applied in autocommit as each connection opens) —
        // same rationale as NotesDatabase: concurrent readers, and wait-don't-throw on a locked db.
        val dataSource = SQLiteDataSource(
            SQLiteConfig().apply {
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setBusyTimeout(5000)
            },
        ).apply { url = "jdbc:sqlite:$path" }
        val db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.create(ProjectsTable)
        }
        return db
    }

    private fun resolvePath(): String =
        System.getProperty("projects.db.path")
            ?: System.getenv("PROJECTS_DB_PATH")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PATH
}
