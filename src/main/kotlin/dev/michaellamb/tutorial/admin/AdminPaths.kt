/*
 * Where the admin forms are mounted: admin.michaellamb.dev/notes and /projects, host-scoped by
 * ADMIN_HOST (see admin.michaellamb.dev, the coordination repo). The old
 * kotlin-tutorial.michaellamb.dev/admin layout is gone; LegacyAdminRedirects.kt sends it here.
 *
 * Every link, form action and redirect in admin/ is built from these, never a literal. CSRF's
 * originMatchesHost() wants a same-host POST, and a hard-coded path would drift from the mount.
 */
package dev.michaellamb.tutorial.admin

data class AdminPaths(val notes: String, val projects: String, val embed: String) {
    companion object {
        // embed-admin is served by node2 at /embed/ on the same host, never by this service.
        val PORTAL = AdminPaths(notes = "/notes", projects = "/projects", embed = "/embed/")
    }
}

/**
 * The admin hostname from `ADMIN_HOST`, or null when unset (then no admin forms are served at all).
 *
 * Hostname only: a port is accepted and dropped, because Ktor's host() selector and
 * `request.host()` compare the Host header *without* its port. So `admin.localhost:8080` and
 * `admin.localhost` both mean "serve the portal at http://admin.localhost:8080" locally.
 */
fun adminHostFromEnv(raw: String? = System.getenv("ADMIN_HOST")): String? =
    raw?.trim()?.substringBefore(':')?.lowercase()?.takeIf { it.isNotBlank() }
