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
 * A TV can't scroll, so the layout is locked to one viewport (see SIGNAGE_CSS). The three
 * cards are deliberately NOT equal widths — each column is sized to what its content actually
 * needs — and commits are a marquee along the bottom rather than a fourth card, since a commit
 * list is the one section that reads fine as a single line in motion. The marquee is only
 * emitted when there are commits, so a quiet day leaves no empty strip on screen.
 *
 * Nobody can tap a signage screen, so a note's link is rendered as a scannable QR code
 * (SignageQr.kt) rather than an anchor.
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

/** Rendered width of a note's QR code, in the page's pre-scale CSS pixels. */
private const val QR_PIXELS = 132

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
            }
            // Emits nothing at all when there are no commits — no empty strip on a quiet day.
            commitTicker(digest.repos)
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
                div("update-text") {
                    p("update-body") { +entry.body }
                    span("update-time") { +humanAgo(entry.createdAt) }
                }
                // A link is dead weight on a display nobody can tap — show it as a code instead.
                entry.url?.let { url ->
                    qrSvg(url, QR_PIXELS)?.let { svg ->
                        div("update-qr") {
                            unsafe { +svg }
                            span("qr-hint") { +"scan" }
                        }
                    }
                }
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
                img(classes = "game-art", src = game.artUrl, alt = "${game.name} cover")
                div("game-meta") {
                    span("game-name") { +game.name }
                    span("game-time") { +"${formatPlaytime(game.recentMinutes)} · last 2 weeks" }
                }
            }
        }
    }
}

/**
 * Bottom marquee of recent commits. Renders nothing when there is nothing to say.
 *
 * The track holds the item list twice and animates to -50%: each half is identical in width
 * (its own trailing gap included, hence `padding-right` rather than a gap on the track), so the
 * halfway point lines the second copy up exactly where the first started and the loop is seamless.
 */
/**
 * Flattens the repo groups into the ticker's `repo -> commit title` line items. Empty when there
 * is nothing to scroll — including the case where a repo group carries no commits at all, which
 * is why the ticker keys off this rather than `repos.isEmpty()`.
 */
internal fun tickerItems(repos: List<DigestRepo>): List<Pair<String, String>> =
    repos.flatMap { repo -> repo.commits.map { repo.repo to it.title } }

private fun FlowContent.commitTicker(repos: List<DigestRepo>) {
    val items = tickerItems(repos)
    if (items.isEmpty()) return
    // Scale the scroll to the amount of text so a long list doesn't crawl and a short one doesn't race.
    val seconds = (items.size * 9).coerceIn(30, 120)
    div("signage-ticker") {
        div("ticker-track") {
            attributes["style"] = "animation-duration: ${seconds}s"
            repeat(2) { copy ->
                div("ticker-half") {
                    if (copy == 1) attributes["aria-hidden"] = "true"
                    items.forEach { (repo, commitTitle) ->
                        span("ticker-item") {
                            span("ticker-repo") { +repo }
                            span("ticker-title") { +commitTitle }
                        }
                    }
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

.signage-page {
  /* A TV can't scroll — hard-cap to one viewport; anything a card can't fit
     gets clipped inside that card (see .signage-card), never below the fold.
     Laid out at 133.33vw/vh and scaled down to 0.75 (not shrunk property by
     property) so every element — fonts, posters, icons, gaps — gets 1/0.75
     more absolute CSS space to work with before anything needs to wrap or
     clip, while still exactly filling the physical screen after the scale. */
  width: 133.333vw;
  height: 133.333vh;
  transform: scale(0.75);
  transform-origin: top left;
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

/* Unequal on purpose: notes need reading width, films need four posters side by side,
   games are a narrow list of tall covers. */
.signage-grid {
  flex: 1;
  min-height: 0;
  padding: 0 32px 20px;
  display: grid;
  grid-template-columns: 3fr 3.5fr 3.5fr;
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
  margin: 0 0 14px;
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
  gap: 16px;
}
/* Progressive enhancement: a lone note centres in the tall card instead of stranding it at the
   top. Browsers without :has just leave it top-aligned, which is also fine. */
.signage-updates:has(> .update-entry:only-child) { justify-content: center; }
.update-entry {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.update-entry:last-child { border-bottom: none; }
.update-text { flex: 1; min-width: 0; }
.update-body {
  margin: 0;
  font-size: 19px;
  line-height: 1.35;
  color: var(--heading);
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.update-time { font-size: 13px; color: var(--text-dim); }
.update-qr { flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: 5px; }
.update-qr .qr { display: block; border-radius: 4px; }
.qr-hint {
  font-size: 10px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-dim);
}

/* 2x2 rather than 1x4: in a full-height column, four posters in a row are limited by width and
   leave most of the card empty, while a 2x2 doubles each poster's width and fills the height.
   Each poster takes its height from the cell and derives width from the aspect ratio (capped at
   100%), so whichever dimension binds first, the art stays in proportion and never crops. */
.signage-films {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 14px 16px;
}
.film-card { min-width: 0; min-height: 0; display: flex; flex-direction: column; align-items: center; }
.film-poster {
  flex: 1;
  min-height: 0;
  width: auto;
  max-width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
  border-radius: 4px;
  background: rgba(255,255,255,0.05);
  display: block;
}
.film-poster--placeholder {
  display: flex; align-items: center; justify-content: center;
  width: 100%;
  font-size: 2rem; opacity: 0.4;
}
.film-meta { margin-top: 8px; font-size: 14px; overflow: hidden; text-align: center; flex: 0 0 auto; }
.film-title {
  font-weight: 600;
  color: var(--heading);
  display: -webkit-box;
  -webkit-line-clamp: 2;
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
  gap: 14px;
}
.game-row { display: flex; align-items: center; gap: 16px; min-height: 0; }
/* 104px wide (156 tall) is the largest that still fits five rows inside the card at 1080p with
   room to spare — the covers are the point of this section, so they get the space. */
.game-art {
  width: 104px;
  aspect-ratio: 2 / 3;
  border-radius: 6px;
  object-fit: cover;
  flex: 0 0 auto;
  background: rgba(255,255,255,0.05);
}
.game-meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.game-name {
  font-size: 18px;
  color: var(--heading);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.game-time { font-size: 13px; color: var(--text-dim); font-variant-numeric: tabular-nums; }

.signage-ticker {
  flex: 0 0 auto;
  margin: 0 32px 20px;
  background: var(--bg-elev);
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
  padding: 11px 0;
}
.ticker-track {
  display: flex;
  width: max-content;
  will-change: transform;
  animation-name: signage-ticker;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
.ticker-half { display: flex; align-items: center; gap: 44px; padding-right: 44px; }
.ticker-item { display: inline-flex; align-items: baseline; gap: 10px; white-space: nowrap; font-size: 14px; }
.ticker-repo { color: var(--accent); font-weight: 600; }
.ticker-title { color: var(--text-dim); }
@keyframes signage-ticker {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}
""".trimIndent()
