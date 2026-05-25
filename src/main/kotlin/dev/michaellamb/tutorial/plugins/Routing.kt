package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.health.healthRoutes
import dev.michaellamb.tutorial.home.homeRoutes
import dev.michaellamb.tutorial.notes.NoteRepository
import dev.michaellamb.tutorial.notes.noteRoutes
import dev.michaellamb.tutorial.tour.collectionRoutes
import dev.michaellamb.tutorial.tour.coroutineRoutes
import dev.michaellamb.tutorial.tour.dataClassRoutes
import dev.michaellamb.tutorial.tour.extensionRoutes
import dev.michaellamb.tutorial.tour.genericsRoutes
import dev.michaellamb.tutorial.tour.higherOrderFunctionRoutes
import dev.michaellamb.tutorial.tour.interfaceRoutes
import dev.michaellamb.tutorial.tour.nullSafetyRoutes
import dev.michaellamb.tutorial.tour.reflectionRoutes
import dev.michaellamb.tutorial.tour.resultRoutes
import dev.michaellamb.tutorial.tour.scopeFunctionRoutes
import dev.michaellamb.tutorial.tour.sealedWhenRoutes
import dev.michaellamb.tutorial.tour.sequenceRoutes
import dev.michaellamb.tutorial.widgets.WidgetCache
import dev.michaellamb.tutorial.widgets.clusterWidget
import dev.michaellamb.tutorial.widgets.githubWidget
import dev.michaellamb.tutorial.widgets.letterboxdWidget
import dev.michaellamb.tutorial.widgets.steamWidget
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val noteRepository = NoteRepository()
    val widgetClient = HttpClient(CIO)
    val widgetCache = WidgetCache()

    routing {
        homeRoutes()
        healthRoutes()

        route("/tour") {
            dataClassRoutes()
            nullSafetyRoutes()
            sealedWhenRoutes()
            coroutineRoutes()
            extensionRoutes()
            scopeFunctionRoutes()
            collectionRoutes()
            genericsRoutes()
            higherOrderFunctionRoutes()
            interfaceRoutes()
            sequenceRoutes()
            resultRoutes()
            reflectionRoutes()
        }

        route("/widgets") {
            letterboxdWidget(widgetClient, widgetCache)
            steamWidget(widgetClient, widgetCache)
            clusterWidget(widgetClient, widgetCache)
            githubWidget(widgetClient, widgetCache)
        }

        noteRoutes(noteRepository)
    }
}
