/*
 * Where the admin forms are mounted. The same handlers serve two layouts while the admin moves to
 * its own hostname (see admin.michaellamb.dev, the coordination repo):
 *
 *   kotlin-tutorial.michaellamb.dev/admin, /admin/projects   — LEGACY, kept during the transition
 *   admin.michaellamb.dev/notes, /projects                   — PORTAL, host-scoped (ADMIN_HOST)
 *
 * Every link, form action and redirect in admin/ is built from one of these, never a literal, so a
 * page always posts back to — and lands on — the host and path it was served from. That matters
 * twice over: CSRF's originMatchesHost() wants a same-host POST, and a hard-coded "/admin" on the
 * admin host would bounce a user onto a path that host doesn't serve.
 */
package dev.michaellamb.tutorial.admin

data class AdminPaths(val notes: String, val projects: String) {
    companion object {
        val LEGACY = AdminPaths(notes = "/admin", projects = "/admin/projects")
        val PORTAL = AdminPaths(notes = "/notes", projects = "/projects")
    }
}

/**
 * The admin hostname from `ADMIN_HOST`, or null when unset (then only the legacy layout exists).
 *
 * Hostname only: a port is accepted and dropped, because Ktor's host() selector and
 * `request.host()` compare the Host header *without* its port. So `admin.localhost:8080` and
 * `admin.localhost` both mean "serve the portal at http://admin.localhost:8080" locally.
 */
fun adminHostFromEnv(raw: String? = System.getenv("ADMIN_HOST")): String? =
    raw?.trim()?.substringBefore(':')?.lowercase()?.takeIf { it.isNotBlank() }
