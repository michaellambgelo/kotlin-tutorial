/*
 * Per-widget TTL cache.
 *
 * Pedagogical focus: kotlinx.coroutines.sync.Mutex — coordinates concurrent
 * reads/writes so only one upstream fetch runs at a time per cache key.
 */
package dev.michaellamb.tutorial.widgets

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

class WidgetCache {
    private data class Entry(val html: String, val expiresAt: Instant)

    private val store = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    suspend fun getOrCompute(key: String, ttl: Duration, fetch: suspend () -> String): String =
        mutex.withLock {
            val now = Instant.now()
            store[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return@withLock it.html }
            val html = fetch()
            store[key] = Entry(html, now.plus(ttl))
            html
        }
}
