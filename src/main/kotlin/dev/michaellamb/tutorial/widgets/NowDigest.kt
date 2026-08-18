/*
 * Combined "/now" digest — a single JSON document aggregating every section of the blog's /now
 * page (curated "Recently updated" feed + Letterboxd watches + Steam plays + GitHub commits).
 *
 * Pedagogical focus: structured concurrency — `coroutineScope { async { } }` fans the four
 * independent upstream fetches out in parallel and joins them, so the digest is as slow as the
 * slowest single source rather than their sum. Each section degrades to empty on failure (mirroring
 * the individual HTML widgets) so one flaky upstream never fails the whole digest.
 *
 * Consumed by the homelab-bot `/now` Discord command. Served via respondText with a pre-serialized
 * string (like the HTML widgets) so the OpenAPI plugin doesn't try to schema the DTOs.
 */
package dev.michaellamb.tutorial.widgets

import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Duration
import java.time.Instant

private const val DIGEST_CACHE_KEY = "now.json"
private val DIGEST_TTL: Duration = Duration.ofSeconds(60)

/** The full /now digest. Empty sections are kept (callers decide whether to omit them). */
data class NowDigest(
    val recentlyUpdated: List<NowEntry>,
    val films: List<DigestFilm>,
    val games: List<DigestGame>,
    val repos: List<DigestRepo>,
)

data class DigestFilm(
    val title: String,
    val year: String?,
    val rating: Double?,
    val link: String?,
    val posterUrl: String?,
    val watchedDate: String?,
)
data class DigestGame(
    val name: String,
    val recentMinutes: Int,
    val totalMinutes: Int,
    val iconUrl: String?,
    val artUrl: String,
    val storeUrl: String,
)
data class DigestRepo(val repo: String, val url: String, val commits: List<DigestCommit>, val latest: Instant)
data class DigestCommit(val sha: String, val title: String, val url: String)

fun Route.nowDigest(client: HttpClient, cache: WidgetCache) {
    get("/now.json") {
        val json = cache.getOrCompute(DIGEST_CACHE_KEY, DIGEST_TTL) {
            nowStoreMapper.writeValueAsString(buildDigest(client))
        }
        call.respondText(json, ContentType.Application.Json)
    }
}

/** Fetches all four /now sections concurrently; any section that throws degrades to empty. */
internal suspend fun buildDigest(client: HttpClient): NowDigest = coroutineScope {
    val updated = async { runCatching { NowStore.list(client) }.getOrElse { emptyList() } }
    val films = async { runCatching { fetchFilms(client) }.getOrElse { emptyList() } }
    val games = async { runCatching { fetchRecentGames(client) }.getOrElse { emptyList() } }
    val repos = async { runCatching { fetchRepoGroups(client) }.getOrElse { emptyList() } }

    NowDigest(
        recentlyUpdated = updated.await(),
        films = films.await().map {
            DigestFilm(it.title, it.year, it.rating, it.link, it.posterUrl, it.watchedDate)
        },
        games = games.await().map {
            DigestGame(
                name = it.name,
                recentMinutes = it.playtimeRecent ?: 0,
                totalMinutes = it.playtimeTotal,
                iconUrl = steamIconUrl(it.appid, it.iconHash),
                artUrl = steamArtUrl(it.appid),
                storeUrl = steamStoreUrl(it.appid),
            )
        },
        repos = repos.await().map { g ->
            DigestRepo(g.repo, g.repoUrl, g.commits.map { DigestCommit(it.shortSha, it.title, it.url) }, g.latest)
        },
    )
}
