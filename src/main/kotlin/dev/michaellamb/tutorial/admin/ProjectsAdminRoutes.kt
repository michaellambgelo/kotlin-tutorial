/*
 * Admin form for the curated /about "Projects" list, served by Ktor at
 * /admin/projects and used to create/edit/delete/reorder the records that
 * /widgets/projects renders.
 *
 * Like AdminRoutes (the "Recently updated" admin), access is enforced at the EDGE
 * by Cloudflare Access — the same self-hosted Access app already covers /admin*,
 * so /admin/projects is gated with zero extra config. Locally there is no Access,
 * so it's open for dev. Writes go straight to the local ProjectRepository (SQLite
 * on the volume); there is no upstream Worker here, unlike the now-store admin.
 *
 * Plain same-origin HTML form POSTs: no JS, no JSON, no CORS preflight. Editing is
 * done by reloading the page with ?edit=<id> (the form pre-fills); reordering is a
 * pair of ▲/▼ submit buttons that POST a move.
 */
package dev.michaellamb.tutorial.admin

import dev.michaellamb.tutorial.projects.Project
import dev.michaellamb.tutorial.projects.ProjectInput
import dev.michaellamb.tutorial.projects.ProjectRepository
import io.ktor.http.ContentType
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.http.Parameters
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.unsafe
import java.util.UUID

private val STATUS_BASE: String =
    System.getenv("UPTIME_KUMA_BASE_URL")?.takeIf { it.isNotBlank() } ?: "https://status.michaellamb.dev"

fun Route.projectsAdminRoutes(repo: ProjectRepository) {
    route("/admin/projects") {
        get {
            val editId = call.request.queryParameters["edit"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val editing = editId?.let { repo.get(it) }
            call.respondText(renderProjectsAdmin(repo.list(), editing), ContentType.Text.Html)
        }

        post {
            val input = call.receiveParameters().toProjectInput()
            if (input != null) repo.create(input)
            call.respondRedirect("/admin/projects")
        }

        post("/{id}/update") {
            val id = call.idParam()
            val input = call.receiveParameters().toProjectInput()
            if (id != null && input != null) repo.update(id, input)
            call.respondRedirect("/admin/projects")
        }

        post("/{id}/delete") {
            call.idParam()?.let { repo.delete(it) }
            call.respondRedirect("/admin/projects")
        }

        post("/{id}/move") {
            val id = call.idParam()
            val up = call.receiveParameters()["dir"] == "up"
            if (id != null) repo.move(id, up)
            call.respondRedirect("/admin/projects")
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.idParam(): UUID? =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

// Parse a submitted form into a ProjectInput. Returns null when the required name/url are blank, so
// the caller skips the write and just redirects (the browser's `required` attrs are the first gate).
private fun Parameters.toProjectInput(): ProjectInput? {
    val name = this["name"]?.trim().orEmpty()
    val url = this["url"]?.trim().orEmpty()
    if (name.isBlank() || url.isBlank()) return null
    return ProjectInput(
        name = name,
        url = url,
        description = this["description"]?.trim().orEmpty(),
        statusMonitorId = this["statusMonitorId"]?.trim()?.toIntOrNull(),
        tech = this["tech"].orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() },
        archived = this["archived"] != null, // unchecked checkboxes aren't submitted at all
    )
}

private fun renderProjectsAdmin(projects: List<Project>, editing: Project?): String =
    "<!DOCTYPE html>\n" + createHTML().html {
        attributes["lang"] = "en"
        head {
            meta(charset = "utf-8")
            meta(name = "viewport", content = "width=device-width, initial-scale=1")
            title { +"projects · admin" }
            style { unsafe { +PROJECTS_ADMIN_CSS } }
        }
        body {
            div("wrap") {
                p("nav") { a(href = "/admin") { +"← Recently updated" } }
                h1 { +"Projects — admin" }
                p("sub") { +"Curate the list shown on the blog's /about page. Order with ▲/▼; archived projects stay here but drop off the page." }

                val editingNow = editing != null
                h2 { +if (editingNow) "Edit project" else "Add project" }
                val action = if (editingNow) "/admin/projects/${editing!!.id}/update" else "/admin/projects"
                form(action = action, method = FormMethod.post, classes = "publish") {
                    label {
                        +"Name"
                        input(type = InputType.text, name = "name") {
                            attributes["required"] = "required"
                            attributes["placeholder"] = "example.michaellamb.dev"
                            value = editing?.name ?: ""
                        }
                    }
                    label {
                        +"URL"
                        input(type = InputType.url, name = "url") {
                            attributes["required"] = "required"
                            attributes["placeholder"] = "https://…"
                            value = editing?.url ?: ""
                        }
                    }
                    label {
                        +"Description"
                        textArea {
                            name = "description"
                            attributes["rows"] = "2"
                            attributes["placeholder"] = "One line about the project"
                            +(editing?.description ?: "")
                        }
                    }
                    label {
                        +"Tech (comma-separated)"
                        input(type = InputType.text, name = "tech") {
                            attributes["placeholder"] = "Kotlin, Ktor"
                            value = editing?.tech?.joinToString(", ") ?: ""
                        }
                    }
                    label {
                        +"Uptime Kuma monitor id (optional)"
                        input(type = InputType.number, name = "statusMonitorId") {
                            attributes["min"] = "0"
                            attributes["placeholder"] = "e.g. 11"
                            value = editing?.statusMonitorId?.toString() ?: ""
                        }
                    }
                    label("check") {
                        input(type = InputType.checkBox, name = "archived") {
                            checked = editing?.archived ?: false
                        }
                        +"Archived (hidden from the page)"
                    }
                    div("row") {
                        button(type = ButtonType.submit) { +if (editingNow) "Save changes" else "Add project" }
                        if (editingNow) {
                            a(href = "/admin/projects", classes = "cancel") { +"Cancel" }
                        }
                    }
                }

                h2 { +"Current projects" }
                if (projects.isEmpty()) {
                    p("empty") { +"No projects yet." }
                } else {
                    val lastIndex = projects.size - 1
                    projects.forEachIndexed { index, prj ->
                        div(if (prj.archived) "entry archived" else "entry") {
                            div("info") {
                                div("title-row") {
                                    a(href = prj.url, target = "_blank") {
                                        attributes["rel"] = "noopener"
                                        +prj.name
                                    }
                                    if (prj.archived) span("tag") { +"archived" }
                                    prj.statusMonitorId?.let { id ->
                                        img(alt = "status", src = "$STATUS_BASE/api/badge/$id/status", classes = "badge")
                                    }
                                }
                                if (prj.description.isNotBlank()) div("desc") { +prj.description }
                                if (prj.tech.isNotEmpty()) div("tech") { +prj.tech.joinToString(" · ") }
                            }
                            div("actions") {
                                moveForm(prj.id, up = true, disabled = index == 0)
                                moveForm(prj.id, up = false, disabled = index == lastIndex)
                                a(href = "/admin/projects?edit=${prj.id}", classes = "btn") { +"Edit" }
                                form(action = "/admin/projects/${prj.id}/delete", method = FormMethod.post, classes = "del") {
                                    button(type = ButtonType.submit, classes = "danger") { +"Delete" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private fun kotlinx.html.FlowContent.moveForm(id: UUID, up: Boolean, disabled: Boolean) {
    form(action = "/admin/projects/$id/move", method = FormMethod.post, classes = "move") {
        hiddenInput(name = "dir") { value = if (up) "up" else "down" }
        button(type = ButtonType.submit, classes = "icon") {
            if (disabled) attributes["disabled"] = "disabled"
            +if (up) "▲" else "▼"
        }
    }
}

private val PROJECTS_ADMIN_CSS = """
:root { color-scheme: light dark; }
* { box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, "Inter", system-ui, sans-serif; margin: 0; padding: 2rem 1rem; line-height: 1.5; }
.wrap { max-width: 680px; margin: 0 auto; }
.nav { margin: 0 0 1rem; font-size: 0.85rem; }
.nav a { color: inherit; opacity: 0.7; text-decoration: none; }
.nav a:hover { text-decoration: underline; }
h1 { font-size: 1.4rem; margin: 0 0 0.25rem; }
h2 { font-size: 1.05rem; margin: 2rem 0 0.5rem; }
.sub { opacity: 0.6; margin: 0 0 1.5rem; font-size: 0.9rem; }
.publish { display: flex; flex-direction: column; gap: 0.7rem; }
.publish label { display: flex; flex-direction: column; gap: 0.3rem; font-size: 0.8rem; opacity: 0.85; }
.publish label.check { flex-direction: row; align-items: center; gap: 0.5rem; opacity: 1; }
.publish input, .publish textarea { width: 100%; font: inherit; padding: 0.55rem 0.7rem; border-radius: 8px; border: 1px solid rgba(127,127,127,0.4); background: transparent; color: inherit; }
.publish label.check input { width: auto; }
.publish textarea { resize: vertical; }
.publish .row { display: flex; gap: 0.8rem; align-items: center; margin-top: 0.3rem; }
button { font: inherit; padding: 0.55rem 1.1rem; border-radius: 999px; border: none; background: #1d6fe0; color: #fff; cursor: pointer; }
button:hover { background: #1860c4; }
.cancel { font-size: 0.85rem; color: inherit; opacity: 0.7; }
.entry { display: flex; gap: 1rem; justify-content: space-between; align-items: flex-start; padding: 0.8rem 0; border-bottom: 1px solid rgba(127,127,127,0.18); }
.entry:last-child { border-bottom: none; }
.entry.archived { opacity: 0.55; }
.entry .title-row { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
.entry .title-row a { color: inherit; font-weight: 600; }
.entry .badge { height: 1.1em; }
.entry .tag { font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.04em; border: 1px solid rgba(127,127,127,0.4); border-radius: 999px; padding: 0.1rem 0.45rem; opacity: 0.8; }
.entry .desc { font-size: 0.88rem; opacity: 0.75; margin-top: 0.2rem; }
.entry .tech { font-size: 0.78rem; opacity: 0.6; margin-top: 0.2rem; }
.entry .actions { display: flex; gap: 0.35rem; align-items: center; flex-shrink: 0; }
.entry .actions .btn, .entry .actions a.btn { font-size: 0.8rem; padding: 0.3rem 0.7rem; border-radius: 999px; border: 1px solid rgba(127,127,127,0.4); background: transparent; color: inherit; cursor: pointer; text-decoration: none; }
.entry .actions .btn:hover { background: rgba(127,127,127,0.12); }
button.icon { padding: 0.25rem 0.5rem; background: transparent; color: inherit; border: 1px solid rgba(127,127,127,0.4); font-size: 0.8rem; }
button.icon:hover:not([disabled]) { background: rgba(127,127,127,0.12); }
button.icon[disabled] { opacity: 0.3; cursor: default; }
button.danger { background: transparent; color: #d65050; border: 1px solid rgba(214,80,80,0.5); padding: 0.3rem 0.8rem; font-size: 0.8rem; }
button.danger:hover { background: rgba(214,80,80,0.12); }
.move { display: inline; }
.del { display: inline; }
.empty { opacity: 0.6; font-style: italic; }
""".trimIndent()
