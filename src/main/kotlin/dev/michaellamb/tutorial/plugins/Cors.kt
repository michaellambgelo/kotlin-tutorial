package dev.michaellamb.tutorial.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors() {
    install(CORS) {
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
