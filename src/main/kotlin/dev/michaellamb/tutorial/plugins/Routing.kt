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
