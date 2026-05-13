package dev.michaellamb.tutorial.plugins

import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

fun Application.configureCors() {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowHost("michaellamb.dev", schemes = listOf("https"))
        allowHost("michaellambgelo.github.io", schemes = listOf("https"))
        allowHost("127.0.0.1:4000")
        allowHost("localhost:4000")
    }
}
