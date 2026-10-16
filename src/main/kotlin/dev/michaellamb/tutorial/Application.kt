package dev.michaellamb.tutorial

import dev.michaellamb.tutorial.admin.adminHostFromEnv
import dev.michaellamb.tutorial.auth.CloudflareAccess
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
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() = moduleWith(adminHost = adminHostFromEnv())

/**
 * [module] with the admin hostname passed in rather than read from `ADMIN_HOST`, so tests can run
 * the real install list with the admin portal on. A separate name, not an overload or a default
 * argument, because application.yaml loads `module` by name.
 */
fun Application.moduleWith(adminHost: String?) {
    // Read once and handed to both plugins that need it, so the provider and the routes that use it
    // can't disagree about whether the Access check is on.
    val access = CloudflareAccess.fromEnv()
    if (access == null) log.warn("Cloudflare Access token check is OFF (CF_ACCESS_TEAM_DOMAIN / CF_ACCESS_AUD unset)")
    configureDependencies()
    configureMonitoring()
    // Install before routing so it wraps every handler's failures.
    configureStatusPages()
    configureSerialization()
    configureHttp(adminHost = adminHost)
    configureCors(adminHost = adminHost)
    configureAuthentication(access)
    configureStreaming()
    configureRouting(access, adminHost = adminHost)
}
