/*
 * The Authentication plugin, configured with one named JWT provider.
 *
 * Providers are NAMED, which is the design worth noticing: `authenticate("demo-jwt") { }` selects
 * one by name, so an app can run several schemes side by side (a session for the browser, a bearer
 * token for an API, basic auth for a health probe) and each route picks the one it wants. An
 * unnamed provider becomes the default.
 *
 * See auth/JwtDemo.kt for why this guards a demo route rather than /admin.
 */
package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.auth.JWT_AUDIENCE
import dev.michaellamb.tutorial.auth.JWT_ISSUER
import dev.michaellamb.tutorial.auth.JWT_REALM
import dev.michaellamb.tutorial.auth.demoAlgorithm
import dev.michaellamb.tutorial.errors.ErrorResponse
import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("demo-jwt") {
            realm = JWT_REALM
            verifier(
                JWT.require(demoAlgorithm)
                    .withIssuer(JWT_ISSUER)
                    .withAudience(JWT_AUDIENCE)
                    .build(),
            )
            // Returning null here means "not authenticated" — the validation step is where you
            // reject a token that verified cryptographically but is still unacceptable.
            validate { credential ->
                if (credential.payload.getClaim("user").asString().isNullOrBlank()) null
                else JWTPrincipal(credential.payload)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("missing or invalid bearer token"),
                )
            }
        }
    }
}
