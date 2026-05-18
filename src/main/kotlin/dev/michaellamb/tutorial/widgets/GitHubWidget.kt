/*
 * GitHub recent-commits widget (last 24 hours).
 *
 * Pedagogical focus: java.time + collection operations — computes a rolling
 * 24-hour cutoff with `Instant` and `Duration`, queries GitHub's search API
 * for commits authored since that instant, then groups results by repository
 * for display.
 *
 * Uses the public /search/commits endpoint. No auth required; a GITHUB_TOKEN
 * env var, if set, raises the rate limit from 10/min to 30/min.
 */
package dev.michaellamb.tutorial.widgets

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h4
import kotlinx.html.li
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.ul
import kotlinx.html.unsafe
import java.time.Duration
import java.time.Instant

private val githubUser: String = System.getenv("GITHUB_USERNAME")?.takeIf { it.isNotBlank() } ?: "michaellambgelo"
private val githubToken: String? = System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }
private const val CACHE_KEY = "github"
private val TTL: Duration = Duration.ofSeconds(60)
private val WINDOW: Duration = Duration.ofHours(24)
private val mapper = jacksonObjectMapper().findAndRegisterModules()

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SearchResponse(val items: List<CommitItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommitItem(
    val sha: String,
    @JsonProperty("html_url") val htmlUrl: String,
    val commit: CommitDetail,
    val repository: Repository,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommitDetail(val author: CommitAuthor, val message: String)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class CommitAuthor(val date: Instant)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Repository(
    @JsonProperty("full_name") val fullName: String,
    @JsonProperty("html_url") val htmlUrl: String,
)

private data class RepoGroup(val repo: String, val repoUrl: String, val commits: List<RenderedCommit>, val latest: Instant)
private data class RenderedCommit(val shortSha: String, val title: String, val url: String)

fun Route.githubWidget(client: HttpClient, cache: WidgetCache) {
    get("/github") {
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            renderGitHub(group(fetchCommits(client)))
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

private suspend fun fetchCommits(client: HttpClient): List<CommitItem> {
    val since = Instant.now().minus(WINDOW)
    val body = client.get("https://api.github.com/search/commits") {
        parameter("q", "author:$githubUser committer-date:>$since")
        parameter("per_page", 100)
        parameter("sort", "committer-date")
        parameter("order", "desc")
        headers {
            append(HttpHeaders.Accept, "application/vnd.github+json")
            githubToken?.let { append(HttpHeaders.Authorization, "Bearer $it") }
        }
    }.bodyAsText()
    return mapper.readValue<SearchResponse>(body).items
}

private fun group(commits: List<CommitItem>): List<RepoGroup> =
    commits
        .groupBy { it.repository }
        .map { (repo, items) ->
            RepoGroup(
                repo = repo.fullName,
                repoUrl = repo.htmlUrl,
                commits = items.map { c ->
                    RenderedCommit(
                        shortSha = c.sha.take(7),
                        title = c.commit.message.lineSequence().first(),
                        url = c.htmlUrl,
                    )
                },
                latest = items.maxOf { it.commit.author.date },
            )
        }
        .sortedByDescending { it.latest }

private fun renderGitHub(groups: List<RepoGroup>): String = createHTML().div("widget github-widget") {
    style { unsafe { +GITHUB_CSS } }
    if (groups.isEmpty()) {
        span("empty") { +"No commits in the last 24 hours." }
        return@div
    }
    groups.forEach { group ->
        h4("repo") {
            a(href = group.repoUrl, target = "_blank") {
                attributes["rel"] = "noopener"
                +group.repo
            }
        }
        ul("commits") {
            group.commits.forEach { c ->
                li {
                    a(href = c.url, target = "_blank") {
                        attributes["rel"] = "noopener"
                        span("sha") { +c.shortSha }
                        +" "
                        +c.title
                    }
                }
            }
        }
    }
}

private val GITHUB_CSS = """
.github-widget { font-family: inherit; }
.github-widget .repo { margin: 1.25rem 0 0.35rem; font-size: 1rem; font-weight: 600; }
.github-widget .repo a { color: inherit; text-decoration: none; }
.github-widget .repo a:hover { text-decoration: underline; }
.github-widget .commits { list-style: none; padding: 0; margin: 0; }
.github-widget .commits li { padding: 0.4rem 0; border-bottom: 1px solid rgba(255,255,255,0.08); font-size: 0.875rem; line-height: 1.4; }
.github-widget .commits li:last-child { border-bottom: none; }
.github-widget .commits a { color: inherit; text-decoration: none; display: block; }
.github-widget .commits a:hover { text-decoration: underline; }
.github-widget .sha { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; opacity: 0.55; font-size: 0.82em; margin-right: 0.4rem; }
.github-widget .empty { opacity: 0.7; font-style: italic; }
""".trimIndent()
