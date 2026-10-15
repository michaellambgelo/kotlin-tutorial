package dev.michaellamb.tutorial.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import dev.michaellamb.tutorial.admin.adminHostFromEnv

/**
 * [adminHost] is the portal's own origin. Its form POSTs carry `Origin: https://<adminHost>`, and behind the
 * tunnel the request reaches us as plain http, so CORS doesn't recognise it as same-origin and, with the host
 * missing from this list, answers an empty 403 before the route's CSRF check runs. Same reason
 * kotlin-tutorial.michaellamb.dev is listed: that's what lets the legacy /admin forms post.
 */
fun Application.configureCors(adminHost: String? = adminHostFromEnv()) {
    install(CORS) {
        adminHost?.let { allowHost(it, schemes = listOf("https")) }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHost("kotlin-tutorial.michaellamb.dev", schemes = listOf("https"))
        allowHost("blog.michaellamb.dev", schemes = listOf("https"))
        allowHost("michaellamb.dev", schemes = listOf("https"))
        allowHost("michaellambgelo.github.io", schemes = listOf("https"))
        allowHost("127.0.0.1:4000")
        allowHost("localhost:4000")
        allowHost("localhost:8080")
    }
}
