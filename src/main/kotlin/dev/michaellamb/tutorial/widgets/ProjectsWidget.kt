/*
 * "Projects" widget — the curated project list embedded in the blog's /about page
 * (replacing what used to be a hand-maintained static <ul>). Served at
 * /widgets/projects as an HTML fragment the blog fetches and injects.
 *
 * Pedagogical focus: a widget whose data source is this service's OWN SQLite store
 * (ProjectRepository) rather than an upstream HTTP API — so the read path is a
 * suspended Exposed transaction, and the same records are maintained from the
 * Cloudflare-Access-gated admin form (see admin/ProjectsAdminRoutes.kt). The 60s
 * WidgetCache still applies, so an admin edit shows up within a minute.
 */
package dev.michaellamb.tutorial.widgets

import dev.michaellamb.tutorial.projects.Project
import dev.michaellamb.tutorial.projects.ProjectRepository
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
import java.time.Duration

private const val CACHE_KEY = "projects"
private val TTL: Duration = Duration.ofSeconds(60)

// Base for the Uptime Kuma status badges, same env the cluster/status widgets read.
private val STATUS_BASE: String =
    System.getenv("UPTIME_KUMA_BASE_URL")?.takeIf { it.isNotBlank() } ?: "https://status.michaellamb.dev"

fun Route.projectsWidget(repo: ProjectRepository, cache: WidgetCache) {
    get("/projects") {
        val html = cache.getOrCompute(CACHE_KEY, TTL) {
            runCatching { renderProjects(repo.listVisible()) }.getOrElse { unavailableProjectsFragment() }
        }
        call.respondText(html, ContentType.Text.Html)
    }
}

private fun badgeUrl(monitorId: Int): String = "$STATUS_BASE/api/badge/$monitorId/status"

private fun renderProjects(projects: List<Project>): String =
    createHTML().div("widget projects-widget") {
        style { unsafe { +PROJECTS_CSS } }
        if (projects.isEmpty()) {
            span("empty") { +"No projects listed yet." }
            return@div
        }
        projects.forEach { p ->
            div("project") {
                div("head") {
                    a(href = p.url, target = "_blank", classes = "name") {
                        attributes["rel"] = "noopener"
                        +p.name
                    }
                    p.statusMonitorId?.let { id ->
                        img(alt = "${p.name} status", src = badgeUrl(id), classes = "badge")
                    }
                }
                if (p.description.isNotBlank()) {
                    div("description") { +p.description }
                }
                if (p.tech.isNotEmpty()) {
                    div("tech") {
                        p.tech.forEach { t -> span("tech-pill") { +t } }
                    }
                }
            }
        }
    }

private fun unavailableProjectsFragment(): String =
    createHTML().div("widget projects-widget") {
        style { unsafe { +PROJECTS_CSS } }
        span("empty") { +"Projects unavailable right now." }
    }

private val PROJECTS_CSS = """
.projects-widget { font-family: inherit; }
.projects-widget .project { padding: 0.8rem 0; border-bottom: 1px solid rgba(127,127,127,0.18); }
.projects-widget .project:last-child { border-bottom: none; }
.projects-widget .head { display: flex; align-items: center; gap: 0.6rem; flex-wrap: wrap; }
.projects-widget .name { font-weight: 600; color: inherit; text-decoration: none; }
.projects-widget .name:hover { text-decoration: underline; }
.projects-widget .badge { height: 1.2em; display: inline-block; vertical-align: middle; }
.projects-widget .description { margin-top: 0.25rem; font-size: 0.95rem; line-height: 1.5; opacity: 0.85; }
.projects-widget .tech { margin-top: 0.45rem; display: flex; gap: 0.4rem; flex-wrap: wrap; }
.projects-widget .tech-pill { font-size: 0.72rem; line-height: 1; padding: 0.28rem 0.5rem; border-radius: 999px; border: 1px solid rgba(127,127,127,0.35); opacity: 0.8; font-variant-numeric: tabular-nums; }
.projects-widget .empty { opacity: 0.7; font-style: italic; }
""".trimIndent()
