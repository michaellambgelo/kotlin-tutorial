/*
 * Steam recently-played widget.
 *
 * Pedagogical focus: data classes + Jackson — Kotlin data classes double as
 * JSON DTOs via @JsonProperty for snake_case → camelCase field mapping.
 */
package dev.michaellamb.tutorial.widgets

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.img
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr
import kotlinx.html.unsafe
import java.time.Duration

private val steamApiKey: String? = System.getenv("STEAM_API_KEY")?.takeIf { it.isNotBlank() }
private val steamId: String? = System.getenv("STEAM_ID")?.takeIf { it.isNotBlank() }
private const val CACHE_KEY = "steam"
private val TTL: Duration = Duration.ofSeconds(60)
private val mapper = jacksonObjectMapper()

@JsonIgnoreProperties(ignoreUnknown = true)
data class SteamGame(
    val appid: Int,
    val name: String,
    @JsonProperty("playtime_2weeks") val playtimeRecent: Int? = null,
    @JsonProperty("playtime_forever") val playtimeTotal: Int = 0,
    @JsonProperty("rtime_last_played") val lastPlayedEpoch: Long? = null,
    @JsonProperty("img_icon_url") val iconHash: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SteamResponse(val response: GameList) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GameList(val games: List<SteamGame> = emptyList())
}

private const val STEAM_CDN = "https://cdn.cloudflare.steamstatic.com/steam/apps"

/**
 * Portrait library art (600x900), addressable from the appid alone — no API key, no server fetch.
 *
 * This is the only art path that serves reliably across the whole catalogue: `capsule_231x87.jpg`,
 * `capsule_616x353.jpg`, and even `header.jpg` 404 for some recent titles (e.g. appid 2968420),
 * while `library_600x900.jpg` resolved for every appid probed, from 2004 releases to 2024 ones.
 */
internal fun steamArtUrl(appid: Int): String = "$STEAM_CDN/$appid/library_600x900.jpg"

/** Public Steam store page for a game. */
internal fun steamStoreUrl(appid: Int): String = "https://store.steampowered.com/app/$appid"

/** Steam's fixed CDN convention for a game's small square icon; null if the API omitted a hash. */
internal fun steamIconUrl(appid: Int, iconHash: String?): String? =
    iconHash?.let { "https://media.steampowered.com/steamcommunity/public/images/apps/$appid/$it.jpg" }

fun Route.steamWidget(client: HttpClient, cache: WidgetCache) {
    get("/steam") {
        if (steamApiKey == null || steamId == null) {
            call.respondText(unconfiguredFragment(), ContentType.Text.Html)
            return@get
        }
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            renderSteam(fetchRecentGames(client))
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

/**
 * Returns games played in the last two weeks (newest first, top 5). Empty when Steam is
 * unconfigured. Shared by the HTML widget and the /now digest.
 */
internal suspend fun fetchRecentGames(client: HttpClient): List<SteamGame> {
    if (steamApiKey == null || steamId == null) return emptyList()
    val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/" +
        "?key=$steamApiKey&steamid=$steamId&include_appinfo=1&include_played_free_games=1&format=json"
    val body = client.get(url).bodyAsText()
    val games = mapper.readValue(body, SteamResponse::class.java).response.games
    return games
        .filter { (it.playtimeRecent ?: 0) > 0 }
        .sortedByDescending { it.lastPlayedEpoch ?: 0L }
        .take(5)
}

private fun formatPlaytime(minutes: Int): String = when {
    minutes >= 60 -> "%.1fh".format(minutes / 60.0)
    else -> "${minutes}m"
}

private fun renderSteam(games: List<SteamGame>): String = createHTML().div("widget steam-widget") {
    style { unsafe { +STEAM_CSS } }
    if (games.isEmpty()) {
        span("empty") { +"No games played in the last two weeks." }
        return@div
    }
    table {
        thead {
            tr {
                th { +"Game" }
                th(classes = "col-time") { +"Last 2 weeks" }
                th(classes = "col-time") { +"All time" }
            }
        }
        tbody {
            games.forEach { g ->
                tr {
                    td("name") {
                        a(href = steamStoreUrl(g.appid), target = "_blank") {
                            attributes["rel"] = "noopener"
                            img(classes = "art", src = steamArtUrl(g.appid), alt = "${g.name} cover") {
                                attributes["loading"] = "lazy"
                            }
                            span("game-name") { +g.name }
                        }
                    }
                    td("time") { +formatPlaytime(g.playtimeRecent ?: 0) }
                    td("time") { +formatPlaytime(g.playtimeTotal) }
                }
            }
        }
    }
}

private fun unconfiguredFragment(): String = createHTML().div("widget steam-widget unconfigured") {
    style { unsafe { +STEAM_CSS } }
    span("empty") { +"Steam widget not configured (set STEAM_API_KEY and STEAM_ID)." }
}

private val STEAM_CSS = """
.steam-widget { font-family: inherit; }
.steam-widget table { width: 100%; border-collapse: collapse; }
.steam-widget th { text-align: left; padding: 0.5rem 0.75rem; font-weight: 500; font-size: 0.85rem; opacity: 0.5; border-bottom: 1px solid rgba(255,255,255,0.15); }
.steam-widget th.col-time { text-align: right; width: 7rem; }
.steam-widget td { padding: 0.6rem 0.75rem; border-bottom: 1px solid rgba(255,255,255,0.08); }
.steam-widget tr:last-child td { border-bottom: none; }
.steam-widget td.name { font-weight: 500; }
.steam-widget td.name a { display: flex; align-items: center; gap: 0.6rem; color: inherit; text-decoration: none; }
.steam-widget td.name a:hover .game-name { text-decoration: underline; }
.steam-widget .art { width: 34px; aspect-ratio: 2 / 3; object-fit: cover; border-radius: 3px; flex: 0 0 auto; background: rgba(255,255,255,0.05); }
.steam-widget td.time { text-align: right; font-variant-numeric: tabular-nums; opacity: 0.8; }
.steam-widget .empty { opacity: 0.7; font-style: italic; }
""".trimIndent()
