/*
 * CSRF protection for the admin forms.
 *
 * Pedagogical focus: a **route-scoped plugin**. Unlike CallLogging or StatusPages, `CSRF` is a
 * `RouteScopedPlugin`, so `install(CSRF)` inside a `route("/admin") { }` block applies to that
 * subtree and nothing else. That distinction matters here: applying it application-wide would break
 * `POST /notes`, which is called cross-origin by the blog and by Swagger's "Try it out".
 *
 * Why it's needed at all: the admin pages are deliberately JS-free — plain same-origin HTML form
 * POSTs (see admin/). A form POST is exactly the request a browser will happily issue from another
 * site with the user's cookies attached, and CORS does not stop it (CORS governs whether the
 * *response* is readable, not whether the request is *sent*). Cloudflare Access proves the user is
 * signed in; it does not prove *this* page issued the request.
 *
 * Kotlin note: a function type with a receiver — `ApplicationCall.(String) -> Unit` in `onFailure`
 * — is the same shape /tour/lambdas-with-receiver builds by hand.
 */
package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.errors.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.csrf.CSRF
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

/**
 * Installs CSRF checks on the surrounding route. Call this *inside* the `/admin` subtree.
 *
 * `originMatchesHost()` compares the Origin header against the request's own Host, which is the
 * right check for same-origin forms and needs no hidden token field, no session, and no JS.
 */
fun Route.installAdminCsrf() {
    install(CSRF) {
        originMatchesHost()
        onFailure { reason ->
            respond(HttpStatusCode.Forbidden, ErrorResponse("CSRF check failed: $reason"))
        }
    }
}
