/*
 * The pre-portal admin URLs, kotlin-tutorial.michaellamb.dev/admin and /admin/projects, now only
 * redirect (301) to the same form on the admin host, keeping the query string so an old
 * `?edit=<id>` link still opens that project. Nothing else under /admin exists: a POST there, the
 * shape of an old form or script, is a 404 rather than a redirect, so it can never change data on
 * either host.
 *
 * Unlike the forms, these are not behind the Access check: a redirect reveals nothing, and being
 * reachable without a login prompt is the point.
 */
package dev.michaellamb.tutorial.admin

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.queryString
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/** Returns the `/admin` route so the caller can hide() it from the generated spec. */
fun Route.legacyAdminRedirects(adminHost: String): Route {
    suspend fun RoutingCall.redirectTo(path: String) {
        val query = request.queryString().takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()
        respondRedirect("https://$adminHost$path$query", permanent = true)
    }

    return route("/admin") {
        get { call.redirectTo(AdminPaths.PORTAL.notes) }
        get("/projects") { call.redirectTo(AdminPaths.PORTAL.projects) }
        route("{rest...}") { handle { call.respond(HttpStatusCode.NotFound) } }
        handle { call.respond(HttpStatusCode.NotFound) }
    }
}
