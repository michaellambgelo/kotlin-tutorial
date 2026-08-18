package dev.michaellamb.tutorial.signage

import dev.michaellamb.tutorial.widgets.DigestCommit
import dev.michaellamb.tutorial.widgets.DigestRepo
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The signage ticker must not exist at all when there is nothing to scroll — an empty strip on a
 * TV reads as a broken widget. `tickerItems` is the predicate the renderer keys off.
 */
class SignageTickerTest {

    private fun repo(name: String, vararg titles: String) = DigestRepo(
        repo = name,
        url = "https://github.com/$name",
        commits = titles.map { DigestCommit("abc1234", it, "https://github.com/$name/commit/abc1234") },
        latest = Instant.parse("2026-08-18T00:00:00Z"),
    )

    @Test
    fun `no repos means no ticker`() {
        assertTrue(tickerItems(emptyList()).isEmpty())
    }

    @Test
    fun `a repo group carrying no commits still means no ticker`() {
        assertTrue(tickerItems(listOf(repo("michaellambgelo/kotlin-tutorial"))).isEmpty())
    }

    @Test
    fun `commits are flattened to repo and title pairs across groups`() {
        val items = tickerItems(
            listOf(
                repo("michaellambgelo/kotlin-tutorial", "feat: cover art", "fix: ticker"),
                repo("michaellambgelo/homelab-bot", "chore: bump"),
            ),
        )
        assertEquals(3, items.size)
        assertEquals("michaellambgelo/kotlin-tutorial" to "feat: cover art", items[0])
        assertEquals("michaellambgelo/homelab-bot" to "chore: bump", items[2])
    }
}
