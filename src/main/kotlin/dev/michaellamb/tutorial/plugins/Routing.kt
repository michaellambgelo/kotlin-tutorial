package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.BuildInfo
import dev.michaellamb.tutorial.admin.adminRoutes
import dev.michaellamb.tutorial.admin.projectsAdminRoutes
import dev.michaellamb.tutorial.health.healthRoutes
import dev.michaellamb.tutorial.home.homeRoutes
import dev.michaellamb.tutorial.notes.NoteRepository
import dev.michaellamb.tutorial.notes.NotesDatabase
import dev.michaellamb.tutorial.notes.noteRoutes
import dev.michaellamb.tutorial.projects.ProjectRepository
import dev.michaellamb.tutorial.projects.ProjectsDatabase
import dev.michaellamb.tutorial.signage.signageRoutes
import dev.michaellamb.tutorial.tour.basicTypeRoutes
import dev.michaellamb.tutorial.tour.collectionRoutes
import dev.michaellamb.tutorial.tour.collectionTypeRoutes
import dev.michaellamb.tutorial.tour.controlFlowRoutes
import dev.michaellamb.tutorial.tour.coroutineRoutes
import dev.michaellamb.tutorial.tour.dataClassRoutes
import dev.michaellamb.tutorial.tour.delegationRoutes
import dev.michaellamb.tutorial.tour.enumRoutes
import dev.michaellamb.tutorial.tour.extensionRoutes
import dev.michaellamb.tutorial.tour.functionRoutes
import dev.michaellamb.tutorial.tour.genericsRoutes
import dev.michaellamb.tutorial.tour.higherOrderFunctionRoutes
import dev.michaellamb.tutorial.tour.interfaceRoutes
import dev.michaellamb.tutorial.tour.lambdaReceiverRoutes
import dev.michaellamb.tutorial.tour.nullSafetyRoutes
import dev.michaellamb.tutorial.tour.objectRoutes
import dev.michaellamb.tutorial.tour.openClassRoutes
import dev.michaellamb.tutorial.tour.optInRoutes
import dev.michaellamb.tutorial.tour.propertyRoutes
import dev.michaellamb.tutorial.tour.reflectionRoutes
import dev.michaellamb.tutorial.tour.resultRoutes
import dev.michaellamb.tutorial.tour.scopeFunctionRoutes
import dev.michaellamb.tutorial.tour.sealedWhenRoutes
import dev.michaellamb.tutorial.tour.sequenceRoutes
import dev.michaellamb.tutorial.tour.smartCastRoutes
import dev.michaellamb.tutorial.tour.valueClassRoutes
import dev.michaellamb.tutorial.tour.variableRoutes
import dev.michaellamb.tutorial.widgets.WidgetCache
import dev.michaellamb.tutorial.widgets.clusterWidget
import dev.michaellamb.tutorial.widgets.githubWidget
import dev.michaellamb.tutorial.widgets.letterboxdWidget
import dev.michaellamb.tutorial.widgets.nowDigest
import dev.michaellamb.tutorial.widgets.projectsWidget
import dev.michaellamb.tutorial.widgets.recentlyUpdatedWidget
import dev.michaellamb.tutorial.widgets.steamWidget
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    val noteRepository = NoteRepository(NotesDatabase.connect())
    val projectRepository = ProjectRepository(ProjectsDatabase.connect()).apply { seedDefaults() }
    val widgetClient = HttpClient(CIO)
    val widgetCache = WidgetCache()

    routing {
        homeRoutes()
        healthRoutes()
        signageRoutes(widgetClient)

        // Ordered to follow the official Kotlin tour (kotlinlang.org/docs/kotlin-tour-welcome.html):
        // the beginner chapters first, then the intermediate ones, then the extras this service
        // adds on top (coroutines, sequences, Result, generics, reflection).
        route("/tour") {
            // Beginner tour
            variableRoutes()
            basicTypeRoutes()
            collectionTypeRoutes()
            controlFlowRoutes()
            functionRoutes()
            dataClassRoutes()
            nullSafetyRoutes()

            // Intermediate tour
            extensionRoutes()
            scopeFunctionRoutes()
            lambdaReceiverRoutes()
            interfaceRoutes()
            delegationRoutes()
            objectRoutes()
            openClassRoutes()
            sealedWhenRoutes()
            enumRoutes()
            valueClassRoutes()
            propertyRoutes()
            smartCastRoutes()
            optInRoutes()

            // Beyond the tour
            collectionRoutes()
            higherOrderFunctionRoutes()
            sequenceRoutes()
            coroutineRoutes()
            resultRoutes()
            genericsRoutes()
            reflectionRoutes()
        }

        route("/widgets") {
            letterboxdWidget(widgetClient, widgetCache)
            steamWidget(widgetClient, widgetCache)
            clusterWidget(widgetClient, widgetCache)
            githubWidget(widgetClient, widgetCache)
            recentlyUpdatedWidget(widgetClient, widgetCache)
            nowDigest(widgetClient, widgetCache)
            projectsWidget(projectRepository, widgetCache)
        }

        // Admin forms, both gated at the edge by Cloudflare Access (One-time-PIN email policy over
        // /admin*). The "Recently updated" admin proxies writes to the now-store Worker; the
        // projects admin writes straight to the local SQLite store on the volume.
        adminRoutes(widgetClient)
        projectsAdminRoutes(projectRepository)

        noteRoutes(noteRepository)

        // Swagger UI at /swagger. The spec is assembled from the live routing tree by the
        // ktor { openApi { } } compiler plugin (see build.gradle.kts), which infers request/
        // response/param schemas from the call.receive/respond/parameters in each handler — so
        // the tour routes stay untouched. Served same-origin, so Try-It-Out needs no CORS hop.
        swaggerUI("/swagger") {
            info = OpenApiInfo(
                title = "kotlin-tutorial",
                version = BuildInfo.version,
                description = "A pedagogical Ktor service — each route demonstrates one Kotlin language feature.",
            )
            source = OpenApiDocSource.Routing(
                contentType = ContentType.Application.Json,
                serializeModel = cleanedOpenApiSerializer(),
            )
        }
    }
}
