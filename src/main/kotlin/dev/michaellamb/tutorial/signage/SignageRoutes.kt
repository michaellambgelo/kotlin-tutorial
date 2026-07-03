/*
 * Full-page "now" digest for digital signage — a TV-friendly view of the same
 * data /widgets/now.json exposes, richer (poster art, box art, ratings, watched
 * dates) since a human is looking at it rather than a JSON consumer. Meant to be
 * cast to a TV via homelab-bot's `/signage` Discord command
 * (`/signage url:https://kotlin-tutorial.michaellamb.dev/signage`).
 *
 * Reuses `buildDigest` (NowDigest.kt) directly rather than the /now.json string
 * cache, since it needs the structured NowDigest object (poster/icon URLs etc.),
 * not pre-serialized JSON — WidgetCache only caches strings.
 *
 * A TV can't scroll, so the layout is a fixed 2x2 grid locked to one viewport
 * (see SIGNAGE_CSS) rather than a page that could grow taller than the screen.
 */
package dev.michaellamb.tutorial.signage

import dev.michaellamb.tutorial.widgets.DigestFilm
import dev.michaellamb.tutorial.widgets.DigestGame
import dev.michaellamb.tutorial.widgets.DigestRepo
import dev.michaellamb.tutorial.widgets.NowDigest
import dev.michaellamb.tutorial.widgets.NowEntry
import dev.michaellamb.tutorial.widgets.buildDigest
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.FlowContent
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.header
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.lang
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import java.time.Duration
import java.time.Instant

private const val REFRESH_SECONDS = 300

fun Route.signageRoutes(client: HttpClient) {
    get("/signage") {
        val digest = buildDigest(client)
        call.respondText(renderSignage(digest), ContentType.Text.Html)
    }
}

private fun renderSignage(digest: NowDigest): String {
    val body = createHTML().html {
        lang = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            meta { httpEquiv = "refresh"; content = REFRESH_SECONDS.toString() }
            title("michaellamb.dev — now")
            link(rel = "icon", href = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>K</text></svg>")
            style { unsafe { +SIGNAGE_CSS } }
        }
        body("signage-page") {
            header("signage-header") {
                span("eyebrow") { +"michaellamb.dev" }
                h1 { +"Now" }
            }
            main("signage-grid") {
                signageCard("Recently updated") { recentlyUpdatedSection(digest.recentlyUpdated) }
                signageCard("Films") { filmsSection(digest.films) }
                signageCard("Games") { gamesSection(digest.games) }
                signageCard("Repos") { reposSection(digest.repos) }
            }
        }
    }
    return "<!DOCTYPE html>\n$body"
}

private fun FlowContent.signageCard(title: String, content: FlowContent.() -> Unit) {
    div("signage-card") {
        h2("signage-card__title") { +title }
        content()
    }
}

private fun FlowContent.recentlyUpdatedSection(entries: List<NowEntry>) {
    if (entries.isEmpty()) {
        p("signage-empty") { +"nothing new right now" }
        return
    }
    div("signage-updates") {
        entries.take(4).forEach { entry ->
            div("update-entry") {
                p("update-body") { +entry.body }
                span("update-time") { +humanAgo(entry.createdAt) }
            }
        }
    }
}

private fun FlowContent.filmsSection(films: List<DigestFilm>) {
    if (films.isEmpty()) {
        p("signage-empty") { +"nothing watched recently" }
        return
    }
    div("signage-films") {
        films.take(4).forEach { film ->
            div("film-card") {
                if (film.posterUrl != null) {
                    img(classes = "film-poster", src = film.posterUrl, alt = "${film.title} poster")
                } else {
                    div("film-poster film-poster--placeholder") { +"🎬" }
                }
                div("film-meta") {
                    span("film-title") { +film.title }
                    if (film.year != null) span("film-year") { +" (${film.year})" }
                    if (film.rating != null) div("film-rating") { +starString(film.rating) }
                }
            }
        }
    }
}

private fun FlowContent.gamesSection(games: List<DigestGame>) {
    if (games.isEmpty()) {
        p("signage-empty") { +"nothing played in the last two weeks" }
        return
    }
    div("signage-games") {
        games.take(5).forEach { game ->
            div("game-row") {
                if (game.iconUrl != null) {
                    img(classes = "game-icon", src = game.iconUrl, alt = "${game.name} icon")
                } else {
                    div("game-icon game-icon--placeholder") { +"🎮" }
                }
                span("game-name") { +game.name }
                span("game-time") { +formatPlaytime(game.recentMinutes) }
            }
        }
    }
}

private fun FlowContent.reposSection(repos: List<DigestRepo>) {
    if (repos.isEmpty()) {
        p("signage-empty") { +"no commits in the last 24 hours" }
        return
    }
    div("signage-repos") {
        repos.take(3).forEach { repo ->
            div("repo-group") {
                div("repo-head") {
                    a(href = repo.url, target = "_blank") {
                        attributes["rel"] = "noopener"
                        +repo.repo
                    }
                    span("repo-time") { +humanAgo(repo.latest) }
                }
                repo.commits.take(3).forEach { commit ->
                    p("repo-commit") { +commit.title }
                }
            }
        }
    }
}

private fun starString(rating: Double): String {
    val full = rating.toInt()
    val half = (rating - full) >= 0.5
    return "★".repeat(full) + (if (half) "½" else "")
}

private fun formatPlaytime(minutes: Int): String = when {
    minutes >= 60 -> "%.1fh".format(minutes / 60.0)
    else -> "${minutes}m"
}

private fun humanAgo(from: Instant): String {
    val secs = Duration.between(from, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        else -> "${secs / 86400}d ago"
    }
}

// Same Darcula/IDE palette as home/HomeRoutes.kt's PAGE_CSS, kept as its own block per this
// codebase's convention of every page/widget inlining its own <style> (no shared stylesheet).
private val SIGNAGE_CSS = """
:root {
  --bg: #1e1f22;
  --bg-elev: #2b2d30;
  --border: #3a3d41;
  --text: #bcbec4;
  --text-dim: #868a91;
  --heading: #e6e6e6;
  --accent: #5394ec;
  --star: #ffb84d;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
a { color: inherit; text-decoration: none; }
a:hover { text-decoration: underline; }

.signage-page {
  /* A TV can't scroll — hard-cap to one viewport; anything a card can't fit
     gets clipped inside that card (see .signage-card), never below the fold. */
  height: 100vh;
  overflow: hidden;
  background: var(--bg);
  color: var(--text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 16px;
  line-height: 1.5;
  display: flex;
  flex-direction: column;
}

.signage-header {
  padding: 20px 32px 12px;
  flex: 0 0 auto;
}
.signage-header .eyebrow {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.signage-header h1 {
  margin: 2px 0 0;
  font-size: 32px;
  font-weight: 600;
  color: var(--heading);
}

.signage-grid {
  flex: 1;
  min-height: 0;
  padding: 0 32px 24px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 20px;
}

.signage-card {
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px 20px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.signage-card__title {
  flex: 0 0 auto;
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.signage-empty { color: var(--text-dim); font-style: italic; margin: 0; }

.signage-updates {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.update-entry { padding-bottom: 8px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.update-entry:last-child { border-bottom: none; }
.update-body {
  margin: 0;
  font-size: 14px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.update-time { font-size: 11px; color: var(--text-dim); }

.signage-films {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  gap: 14px;
}
.film-card { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.film-poster {
  width: 100%;
  flex: 1;
  min-height: 0;
  object-fit: cover;
  border-radius: 4px;
  background: rgba(255,255,255,0.05);
  display: block;
}
.film-poster--placeholder {
  display: flex; align-items: center; justify-content: center;
  font-size: 2rem; opacity: 0.4;
}
.film-meta { margin-top: 6px; font-size: 12px; overflow: hidden; }
.film-title {
  font-weight: 600;
  color: var(--heading);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.film-year { color: var(--text-dim); }
.film-rating { color: var(--star); margin-top: 2px; }

.signage-games {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.game-row { display: flex; align-items: center; gap: 12px; }
.game-icon { width: 36px; height: 36px; border-radius: 4px; object-fit: cover; flex: 0 0 auto; }
.game-icon--placeholder { display: flex; align-items: center; justify-content: center; font-size: 1.2rem; background: rgba(255,255,255,0.05); }
.game-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--heading); }
.game-time { color: var(--text-dim); font-variant-numeric: tabular-nums; flex: 0 0 auto; }

.signage-repos {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.repo-group { min-height: 0; overflow: hidden; }
.repo-head { display: flex; justify-content: space-between; gap: 10px; font-weight: 600; color: var(--accent); }
.repo-time { color: var(--text-dim); font-weight: 400; font-size: 11px; }
.repo-commit {
  margin: 4px 0 0;
  font-size: 12.5px;
  color: var(--text-dim);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
""".trimIndent()
