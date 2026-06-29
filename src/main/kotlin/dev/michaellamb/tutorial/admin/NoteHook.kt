/*
 * Fire-and-forget notifier: after a note is persisted to now-store, POST it to homelab-bot's
 * internal `/hooks/now-entry` so the bot posts a Discord embed to #general.
 *
 * No-op (feature-flagged off) unless BOTH `HOMELAB_BOT_NOTE_HOOK_URL` and `NOTE_HOOK_SECRET` are
 * set — mirroring the other optional integrations. The call is LAN-internal (node5 → node4); the
 * shared secret is presented as a bearer token so the (otherwise open) endpoint can authenticate it.
 */
package dev.michaellamb.tutorial.admin

import dev.michaellamb.tutorial.widgets.NowEntry
import dev.michaellamb.tutorial.widgets.nowStoreMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

object NoteHook {
    private val hookUrl: String? = System.getenv("HOMELAB_BOT_NOTE_HOOK_URL")?.takeIf { it.isNotBlank() }
    private val secret: String? = System.getenv("NOTE_HOOK_SECRET")?.takeIf { it.isNotBlank() }

    val enabled: Boolean get() = hookUrl != null && secret != null

    suspend fun announce(client: HttpClient, entry: NowEntry) {
        announce(client, entry, hookUrl ?: return, secret ?: return)
    }

    /** Injectable seam: posts the note to [target] with a [token] bearer header. */
    internal suspend fun announce(client: HttpClient, entry: NowEntry, target: String, token: String) {
        val payload = buildMap<String, Any?> {
            put("body", entry.body)
            put("url", entry.url)
            put("createdAt", entry.createdAt.toString())
        }
        client.post(target) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(nowStoreMapper.writeValueAsString(payload))
        }
    }
}
