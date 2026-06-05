package dev.michaellamb.tutorial.projects

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRepositoryTest {

    // On-disk temp DB per test (not :memory:) — newSuspendedTransaction may open a distinct
    // connection per transaction, which would lose an in-memory DB between calls.
    @TempDir
    lateinit var tmp: File
    private lateinit var repo: ProjectRepository

    @BeforeTest
    fun setup() {
        val db = ProjectsDatabase.connect(File(tmp, "projects-${UUID.randomUUID()}.db").absolutePath)
        repo = ProjectRepository(db)
    }

    private fun input(name: String, archived: Boolean = false) = ProjectInput(
        name = name,
        url = "https://$name",
        description = "$name description",
        statusMonitorId = 7,
        tech = listOf("Kotlin", "Ktor"),
        archived = archived,
    )

    @Test
    fun `create assigns increasing positions and round-trips fields`() = runBlocking {
        val a = repo.create(input("alpha"))
        val b = repo.create(input("bravo"))
        assertTrue(b.position > a.position, "second create gets a higher position")

        val fetched = repo.get(a.id)!!
        assertEquals("alpha description", fetched.description)
        assertEquals(listOf("Kotlin", "Ktor"), fetched.tech)
        assertEquals(7, fetched.statusMonitorId)
    }

    @Test
    fun `listVisible excludes archived`() = runBlocking {
        repo.create(input("visible"))
        repo.create(input("hidden", archived = true))

        val visible = repo.listVisible().map { it.name }
        assertTrue("visible" in visible)
        assertFalse("hidden" in visible)
        // admin list still sees both
        assertEquals(2, repo.list().size)
    }

    @Test
    fun `move swaps order and is a no-op at the ends`() = runBlocking {
        val a = repo.create(input("alpha"))
        val b = repo.create(input("bravo"))
        assertEquals(listOf("alpha", "bravo"), repo.list().map { it.name })

        assertTrue(repo.move(b.id, up = true))
        assertEquals(listOf("bravo", "alpha"), repo.list().map { it.name })

        // bravo is now at the top — moving up again does nothing
        assertFalse(repo.move(b.id, up = true))
        assertEquals(listOf("bravo", "alpha"), repo.list().map { it.name })
        // unknown id is also a no-op
        assertFalse(repo.move(UUID.randomUUID(), up = false))

        // alpha (bottom) cannot move down
        assertFalse(repo.move(a.id, up = false))
    }

    @Test
    fun `update changes fields and delete removes`() = runBlocking {
        val a = repo.create(input("alpha"))

        val updated = repo.update(a.id, input("alpha").copy(name = "alpha2", tech = listOf("X"), archived = true))!!
        assertEquals("alpha2", updated.name)
        assertTrue(updated.archived)
        assertEquals(listOf("X"), repo.get(a.id)!!.tech)

        assertTrue(repo.delete(a.id))
        assertNull(repo.get(a.id))
        assertNull(repo.update(a.id, input("ghost")))
    }

    @Test
    fun `seedDefaults seeds the current projects exactly once`() = runBlocking {
        repo.seedDefaults()
        val seeded = repo.list()
        assertTrue(seeded.size >= 3)
        assertTrue(seeded.any { it.name == "letterboxd.michaellamb.dev" })

        repo.seedDefaults() // idempotent: table is non-empty, so no re-seed
        assertEquals(seeded.size, repo.list().size)
    }
}
