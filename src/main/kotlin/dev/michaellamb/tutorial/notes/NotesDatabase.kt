package dev.michaellamb.tutorial.notes

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.io.File

// Owns the SQLite connection + schema for /notes. connect() is called once per Application (from
// plugins/Routing.kt) and returns a Database handle that NoteRepository runs its transactions on.
object NotesDatabase {
    // Default lives under build/ (gitignored) so dev runs never commit a DB file. The container
    // overrides via NOTES_DB_PATH=/app/data/notes.db (a mounted volume); tests override via the
    // notes.db.path system property (so each testApplication gets an isolated DB).
    private const val DEFAULT_PATH = "build/notes.db"

    fun connect(path: String = resolvePath()): Database {
        File(path).absoluteFile.parentFile?.mkdirs() // non-root `app` user must create ./data
        // WAL (concurrent readers alongside the single writer) + busy_timeout (wait, don't throw,
        // on a momentarily-locked db) are set on the DataSource, applied in autocommit when each
        // connection opens. They can't be `exec()`'d inside an Exposed transaction — SQLite rejects
        // `PRAGMA journal_mode=WAL` from within a transaction.
        val dataSource = SQLiteDataSource(
            SQLiteConfig().apply {
                setJournalMode(SQLiteConfig.JournalMode.WAL)
                setBusyTimeout(5000)
            },
        ).apply { url = "jdbc:sqlite:$path" }
        val db = Database.connect(dataSource)
        transaction(db) {
            SchemaUtils.create(NotesTable)
        }
        return db
    }

    private fun resolvePath(): String =
        System.getProperty("notes.db.path")
            ?: System.getenv("NOTES_DB_PATH")?.takeIf { it.isNotBlank() }
            ?: DEFAULT_PATH
}
