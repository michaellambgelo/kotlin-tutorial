package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.BuildInfo
import dev.michaellamb.tutorial.admin.adminRoutes
import dev.michaellamb.tutorial.auth.jwtDemoRoutes
import dev.michaellamb.tutorial.admin.projectsAdminRoutes
import dev.michaellamb.tutorial.health.healthRoutes
import dev.michaellamb.tutorial.home.homeRoutes
import dev.michaellamb.tutorial.htmx.htmxRoutes
import dev.michaellamb.tutorial.notes.NoteRepository
import dev.michaellamb.tutorial.notes.noteRoutes
import dev.michaellamb.tutorial.resources.tourArchiveRoutes
import dev.michaellamb.tutorial.stream.digestStreamRoutes
import dev.michaellamb.tutorial.projects.ProjectRepository
import dev.michaellamb.tutorial.signage.signageRoutes
import dev.michaellamb.tutorial.tour.basicTypeRoutes
import dev.michaellamb.tutorial.tour.collectionRoutes
import dev.michaellamb.tutorial.tour.comparatorRoutes
import dev.michaellamb.tutorial.tour.contextParameterRoutes
import dev.michaellamb.tutorial.tour.contractRoutes
import dev.michaellamb.tutorial.tour.flowRoutes
import dev.michaellamb.tutorial.tour.javaInteropRoutes
import dev.michaellamb.tutorial.tour.operatorRoutes
import dev.michaellamb.tutorial.tour.propertyDelegateRoutes
import dev.michaellamb.tutorial.tour.recursionRoutes
import dev.michaellamb.tutorial.tour.typeAliasRoutes
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
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    // `by dependencies` — a property delegate resolving on declared type, registered in
    // plugins/Dependencies.kt. These used to be constructed inline here.
    val noteRepository: NoteRepository by dependencies
    val projectRepository: ProjectRepository by dependencies
    val widgetClient: HttpClient by dependencies
    val widgetCache: WidgetCache by dependencies

    routing {
        homeRoutes()
        healthRoutes()
        signageRoutes(widgetClient)

        // Prometheus scrape target. MicrometerMetrics (plugins/Monitoring.kt) records the timings;
        // this is the only place that renders them.
        get("/metrics") {
            call.respondText(meterRegistry.scrape(), ContentType.Text.Plain)
        }

        // Type-safe routing from @Resource classes — see resources/TourArchive.kt.
        tourArchiveRoutes()

        // Server-driven interactivity with no hand-written JS — see htmx/HtmxDemo.kt.
        htmxRoutes()

        // Live digest over SSE and WebSockets, fed by a cold Flow — see stream/DigestStream.kt.
        digestStreamRoutes(widgetClient)

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
            comparatorRoutes()
            sequenceRoutes()
            coroutineRoutes()
            flowRoutes()
            resultRoutes()
            genericsRoutes()
            operatorRoutes()
            propertyDelegateRoutes()
            typeAliasRoutes()
            recursionRoutes()
            contractRoutes()
            contextParameterRoutes()
            reflectionRoutes()
            javaInteropRoutes()
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

        // A JWT demo that guards nothing real — see auth/JwtDemo.kt for why.
        jwtDemoRoutes()

        // Swagger UI at /swagger. The spec is assembled from the live routing tree by the
        // ktor { openApi { } } compiler plugin (see build.gradle.kts), which infers request/
        // response/param schemas from the call.receive/respond/parameters in each handler — so
        // the tour routes stay untouched. Served same-origin, so Try-It-Out needs no CORS hop.
        swaggerUI("/swagger") {
            // Without this Ktor emits `deepLinking: false` and every /swagger#/Tag/operationId
            // anchor the home page builds is inert. See catalog/EndpointCatalog.kt#swaggerHref.
            deepLinking = true
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
