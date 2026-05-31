package dev.michaellamb.tutorial.health

import dev.michaellamb.tutorial.BuildInfo
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import java.lang.management.ManagementFactory

private val startTimeMillis: Long = ManagementFactory.getRuntimeMXBean().startTime

@Serializable
data class HealthResponse(
    val status: String,
    val uptimeSeconds: Long,
    val version: String,
)

fun Route.healthRoutes() {
    get("/health") {
        val uptime = (System.currentTimeMillis() - startTimeMillis) / 1000
        call.respond(HealthResponse(status = "ok", uptimeSeconds = uptime, version = BuildInfo.version))
    }
}
