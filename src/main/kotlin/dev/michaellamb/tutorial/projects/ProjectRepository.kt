package dev.michaellamb.tutorial.projects

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// Exposed DSL repository backing /widgets/projects and /admin/projects. Each query method is a
// suspended transaction on Dispatchers.IO (blocking SQLite JDBC off Netty's event loop), mirroring
// NoteRepository. The list is small and curated, so reorder/create compute max/neighbour positions
// in memory rather than with SQL window functions — clearer, and the row count is a handful.
class ProjectRepository(private val db: Database) {

    private val techSeparator = ","

    private fun ResultRow.toProject() = Project(
        id = this[ProjectsTable.id],
        name = this[ProjectsTable.name],
        url = this[ProjectsTable.url],
        description = this[ProjectsTable.description],
        statusMonitorId = this[ProjectsTable.statusMonitorId],
        tech = this[ProjectsTable.tech].splitTech(),
        archived = this[ProjectsTable.archived],
        position = this[ProjectsTable.position],
        createdAt = Instant.ofEpochMilli(this[ProjectsTable.createdAt]),
    )

    private fun String.splitTech(): List<String> =
        split(techSeparator).map { it.trim() }.filter { it.isNotEmpty() }

    private fun List<String>.joinTech(): String =
        joinToString(techSeparator) { it.trim() } // splitTech() trims again on read, so this is safe

    // All projects, position ascending (createdAt as a stable tiebreak). Used by admin.
    suspend fun list(): List<Project> = newSuspendedTransaction(Dispatchers.IO, db) {
        ProjectsTable.selectAll()
            .orderBy(ProjectsTable.position to SortOrder.ASC, ProjectsTable.createdAt to SortOrder.ASC)
            .map { it.toProject() }
    }

    // Visible (non-archived) projects, position ascending. Used by the public widget.
    suspend fun listVisible(): List<Project> = newSuspendedTransaction(Dispatchers.IO, db) {
        ProjectsTable.selectAll()
            .where { ProjectsTable.archived eq false }
            .orderBy(ProjectsTable.position to SortOrder.ASC, ProjectsTable.createdAt to SortOrder.ASC)
            .map { it.toProject() }
    }

    suspend fun get(id: UUID): Project? = newSuspendedTransaction(Dispatchers.IO, db) {
        ProjectsTable.selectAll().where { ProjectsTable.id eq id }.singleOrNull()?.toProject()
    }

    suspend fun create(input: ProjectInput): Project = newSuspendedTransaction(Dispatchers.IO, db) {
        val nextPosition = (ProjectsTable.selectAll().maxOfOrNull { it[ProjectsTable.position] } ?: 0) + 1
        val project = Project(
            id = UUID.randomUUID(),
            name = input.name,
            url = input.url,
            description = input.description,
            statusMonitorId = input.statusMonitorId,
            tech = input.tech,
            archived = input.archived,
            position = nextPosition,
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )
        ProjectsTable.insert { it.fromProject(project) }
        project
    }

    suspend fun update(id: UUID, input: ProjectInput): Project? = newSuspendedTransaction(Dispatchers.IO, db) {
        val existing = ProjectsTable.selectAll().where { ProjectsTable.id eq id }.singleOrNull()?.toProject()
            ?: return@newSuspendedTransaction null
        val updated = existing.copy(
            name = input.name,
            url = input.url,
            description = input.description,
            statusMonitorId = input.statusMonitorId,
            tech = input.tech,
            archived = input.archived,
        )
        ProjectsTable.update({ ProjectsTable.id eq id }) {
            it[name] = updated.name
            it[url] = updated.url
            it[description] = updated.description
            it[statusMonitorId] = updated.statusMonitorId
            it[tech] = updated.tech.joinTech()
            it[archived] = updated.archived
        }
        updated
    }

    suspend fun delete(id: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO, db) {
        ProjectsTable.deleteWhere { ProjectsTable.id eq id } > 0
    }

    // Swap this project's stored position with its neighbour in the ordered list (up = toward the
    // top). Swapping the two position values reorders correctly even if positions aren't contiguous
    // after deletes. No-op (returns false) at the ends or for an unknown id.
    suspend fun move(id: UUID, up: Boolean): Boolean = newSuspendedTransaction(Dispatchers.IO, db) {
        val ordered = ProjectsTable.selectAll()
            .orderBy(ProjectsTable.position to SortOrder.ASC, ProjectsTable.createdAt to SortOrder.ASC)
            .map { it.toProject() }
        val index = ordered.indexOfFirst { it.id == id }
        if (index < 0) return@newSuspendedTransaction false
        val swapIndex = if (up) index - 1 else index + 1
        if (swapIndex !in ordered.indices) return@newSuspendedTransaction false

        val a = ordered[index]
        val b = ordered[swapIndex]
        ProjectsTable.update({ ProjectsTable.id eq a.id }) { it[position] = b.position }
        ProjectsTable.update({ ProjectsTable.id eq b.id }) { it[position] = a.position }
        true
    }

    // Insert the current /about projects on first run (empty table) so the widget has content
    // immediately after deploy, preserving what the static page showed. Blocking transaction —
    // called once at startup from configureRouting(), before the server accepts requests. If all
    // projects are later deleted via admin, a redeploy re-seeds them; acceptable for this service.
    fun seedDefaults() = transaction(db) {
        if (!ProjectsTable.selectAll().empty()) return@transaction
        DEFAULT_PROJECTS.forEachIndexed { index, seed ->
            ProjectsTable.insert {
                it[id] = UUID.randomUUID()
                it[name] = seed.name
                it[url] = seed.url
                it[description] = seed.description
                it[statusMonitorId] = seed.statusMonitorId
                it[tech] = seed.tech.joinTech()
                it[archived] = seed.archived
                it[position] = index + 1
                it[createdAt] = Instant.now().truncatedTo(ChronoUnit.MILLIS).toEpochMilli()
            }
        }
    }

    private fun org.jetbrains.exposed.sql.statements.InsertStatement<*>.fromProject(p: Project) {
        this[ProjectsTable.id] = p.id
        this[ProjectsTable.name] = p.name
        this[ProjectsTable.url] = p.url
        this[ProjectsTable.description] = p.description
        this[ProjectsTable.statusMonitorId] = p.statusMonitorId
        this[ProjectsTable.tech] = p.tech.joinTech()
        this[ProjectsTable.archived] = p.archived
        this[ProjectsTable.position] = p.position
        this[ProjectsTable.createdAt] = p.createdAt.toEpochMilli()
    }

    companion object {
        // The three entries the static /about Projects list carried, with tech tags added.
        private val DEFAULT_PROJECTS = listOf(
            ProjectInput(
                name = "blog.michaellamb.dev",
                url = "https://blog.michaellamb.dev",
                description = "This blog",
                statusMonitorId = 10,
                tech = listOf("Jekyll", "Ruby", "Bootstrap"),
                archived = false,
            ),
            ProjectInput(
                name = "letterboxd.michaellamb.dev",
                url = "https://letterboxd.michaellamb.dev",
                description = "Custom Letterboxd stats page",
                statusMonitorId = 11,
                tech = listOf("Python", "JavaScript"),
                archived = false,
            ),
            ProjectInput(
                name = "boxd-card.com",
                url = "https://boxd-card.com",
                description = "Shareable PNG cards from a Letterboxd profile",
                statusMonitorId = 12,
                tech = listOf("React", "TypeScript", "Vite"),
                archived = false,
            ),
        )
    }
}
