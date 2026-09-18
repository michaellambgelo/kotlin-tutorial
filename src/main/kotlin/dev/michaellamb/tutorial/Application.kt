package dev.michaellamb.tutorial

import dev.michaellamb.tutorial.plugins.configureAuthentication
import dev.michaellamb.tutorial.plugins.configureCors
import dev.michaellamb.tutorial.plugins.configureDependencies
import dev.michaellamb.tutorial.plugins.configureHttp
import dev.michaellamb.tutorial.plugins.configureMonitoring
import dev.michaellamb.tutorial.plugins.configureRouting
import dev.michaellamb.tutorial.plugins.configureSerialization
import dev.michaellamb.tutorial.plugins.configureStatusPages
import dev.michaellamb.tutorial.plugins.configureStreaming
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureDependencies()
    configureMonitoring()
    // Install before routing so it wraps every handler's failures.
    configureStatusPages()
    configureSerialization()
    configureHttp()
    configureCors()
    configureAuthentication()
    configureStreaming()
    configureRouting()
}
