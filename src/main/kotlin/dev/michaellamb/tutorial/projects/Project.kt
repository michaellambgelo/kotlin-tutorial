package dev.michaellamb.tutorial.projects

import java.time.Instant
import java.util.UUID

// A curated entry for the "Projects" section of the blog's /about page, rendered by
// /widgets/projects and maintained at /admin/projects. Plain Jackson data class (like
// widgets.NowEntry) — there is no JSON REST surface for projects, so it carries no @Serializable:
// the widget renders HTML and the admin posts plain HTML forms. ProjectRepository maps rows by hand.
//
// `tech` is stored as a comma-separated text column and exposed here as a List<String>.
// `statusMonitorId` is the Uptime Kuma monitor id behind the live status badge (null = no badge).
// `position` drives the on-page order (ascending); `archived` hides an entry from the widget while
// keeping it in admin.
data class Project(
    val id: UUID,
    val name: String,
    val url: String,
    val description: String,
    val statusMonitorId: Int?,
    val tech: List<String>,
    val archived: Boolean,
    val position: Int,
    val createdAt: Instant,
)

// The editable fields of a project — what the admin form submits on create/update. `position` and
// `createdAt` are assigned by the repository, not the form, so they're absent here.
data class ProjectInput(
    val name: String,
    val url: String,
    val description: String,
    val statusMonitorId: Int?,
    val tech: List<String>,
    val archived: Boolean,
)
