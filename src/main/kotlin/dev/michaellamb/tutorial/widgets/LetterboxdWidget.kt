/*
 * Letterboxd recent-watches widget.
 *
 * Pedagogical focus: kotlinx.html DSL — type-safe HTML construction in Kotlin.
 * Fetches the public RSS feed, parses it with javax.xml, and emits a `<div>`
 * fragment with a card grid (poster + title + rating + watched date).
 */
package dev.michaellamb.tutorial.widgets

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
import kotlinx.html.unsafe
import org.w3c.dom.Element
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

private val letterboxdUsername: String = System.getenv("LETTERBOXD_USERNAME")?.takeIf { it.isNotBlank() } ?: "michaellamb"
private val feedUrl = "https://letterboxd.com/$letterboxdUsername/rss/"
private const val CACHE_KEY = "letterboxd"
private val TTL: Duration = Duration.ofSeconds(60)
private val POSTER_REGEX = Regex("""<img[^>]+src="([^"]+)"""")

data class Film(
    val title: String,
    val year: String?,
    val rating: Double?,
    val watchedDate: String?,
    val link: String?,
    val posterUrl: String?,
)

fun Route.letterboxdWidget(client: HttpClient, cache: WidgetCache) {
    get("/letterboxd") {
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            renderLetterboxd(fetchFilms(client))
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

/** Fetches the public RSS feed and parses the most recent watches. Shared by the HTML widget and the /now digest. */
internal suspend fun fetchFilms(client: HttpClient): List<Film> {
    val rss = client.get(feedUrl).bodyAsText()
    return parseLetterboxd(rss)
}

private fun parseLetterboxd(rss: String): List<Film> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        .parse(rss.byteInputStream())
    val items = doc.getElementsByTagName("item")
    return (0 until minOf(items.length, 4)).map { i ->
        val item = items.item(i) as Element
        val description = item.firstChildText("description") ?: ""
        Film(
            title = item.firstChildText("letterboxd:filmTitle") ?: "Unknown",
            year = item.firstChildText("letterboxd:filmYear"),
            rating = item.firstChildText("letterboxd:memberRating")?.toDoubleOrNull(),
            watchedDate = item.firstChildText("letterboxd:watchedDate"),
            link = item.firstChildText("link"),
            posterUrl = POSTER_REGEX.find(description)?.groupValues?.getOrNull(1),
        )
    }
}

private fun Element.firstChildText(tag: String): String? =
    getElementsByTagName(tag).item(0)?.textContent?.takeIf { it.isNotBlank() }

private fun starString(rating: Double): String {
    val full = rating.toInt()
    val half = (rating - full) >= 0.5
    return "★".repeat(full) + (if (half) "½" else "") + "☆".repeat(5 - full - (if (half) 1 else 0))
}

private fun renderLetterboxd(films: List<Film>): String = createHTML().div("widget letterboxd-widget") {
    style { unsafe { +LETTERBOXD_CSS } }
    div("grid") {
        films.forEach { film ->
            div("card") {
                val cardContent: kotlinx.html.FlowContent.() -> Unit = {
                    if (film.posterUrl != null) {
                        img(classes = "poster", src = film.posterUrl, alt = "${film.title} poster")
                    } else {
                        div("poster placeholder") { +"🎬" }
                    }
                    div("meta") {
                        span("title") { +film.title }
                        if (film.year != null) span("year") { +" (${film.year})" }
                        if (film.rating != null) div("rating") { +starString(film.rating) }
                        if (film.watchedDate != null) div("watched") { +film.watchedDate }
                    }
                }
                if (film.link != null) {
                    a(href = film.link, target = "_blank") {
                        attributes["rel"] = "noopener"
                        cardContent()
                    }
                } else {
                    cardContent()
                }
            }
        }
    }
}

private val LETTERBOXD_CSS = """
.letterboxd-widget { font-family: inherit; }
.letterboxd-widget .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1rem; }
.letterboxd-widget .card a { display: block; color: inherit; text-decoration: none; }
.letterboxd-widget .card a:hover .title { text-decoration: underline; }
.letterboxd-widget .poster { width: 100%; aspect-ratio: 2 / 3; object-fit: cover; border-radius: 4px; background: rgba(255,255,255,0.05); display: block; }
.letterboxd-widget .poster.placeholder { display: flex; align-items: center; justify-content: center; font-size: 2rem; opacity: 0.4; }
.letterboxd-widget .meta { margin-top: 0.5rem; font-size: 0.875rem; line-height: 1.4; }
.letterboxd-widget .title { font-weight: 600; }
.letterboxd-widget .year { opacity: 0.6; }
.letterboxd-widget .rating { color: #ffb84d; margin-top: 0.15rem; }
.letterboxd-widget .watched { opacity: 0.55; font-size: 0.8rem; margin-top: 0.1rem; font-variant-numeric: tabular-nums; }
""".trimIndent()
