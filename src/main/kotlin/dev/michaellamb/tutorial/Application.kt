package dev.michaellamb.tutorial

import dev.michaellamb.tutorial.plugins.configureCors
import dev.michaellamb.tutorial.plugins.configureMonitoring
import dev.michaellamb.tutorial.plugins.configureRouting
import dev.michaellamb.tutorial.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureCors()
    configureRouting()
}
