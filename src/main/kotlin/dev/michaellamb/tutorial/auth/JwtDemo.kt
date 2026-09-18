/*
 * A JWT demonstration at /demo-auth — deliberately NOT how this service is actually secured.
 *
 * Read this first: the real `/admin*` surface is gated at the edge by Cloudflare Access, which
 * authenticates the user before the request ever reaches Ktor. Adding a second, in-app auth scheme
 * over the same routes would be worse than useless — two systems disagreeing about who is signed
 * in. So these routes protect nothing real. They exist because "how does bearer auth work in Ktor"
 * is a fair question, and the honest answer includes *why this app doesn't use it*.
 *
 * Pedagogical focus: **named authentication providers, and what a JWT does and does not prove**.
 * A JWT is signed, not encrypted — the payload is base64, readable by anyone holding the token.
 * That is why the /demo-auth/decode route can show you the claims without the secret: signing
 * proves the token was not *altered*, it does nothing to keep it *private*. Never put a secret in
 * a claim.
 *
 * Kotlin note: `authenticate("name") { }` is a route-scoped block, the same nesting idea as the
 * CSRF install in plugins/Csrf.kt.
 */
package dev.michaellamb.tutorial.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.michaellamb.tutorial.errors.ApiException
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.Date

internal const val JWT_REALM = "kotlin-tutorial demo"
internal const val JWT_ISSUER = "kotlin-tutorial"
internal const val JWT_AUDIENCE = "kotlin-tutorial-demo"

/**
 * The signing secret.
 *
 * Hard-coded on purpose: these routes guard nothing, and a demo that reads a real secret from the
 * environment would invite someone to believe it protects something. A production provider reads
 * its key from configuration — see the Cloudflare Access service-token pattern in widgets/ for how
 * this service handles a credential it actually cares about.
 */
private const val DEMO_SECRET = "not-a-secret-these-routes-protect-nothing"

internal val demoAlgorithm: Algorithm = Algorithm.HMAC256(DEMO_SECRET)

private const val TOKEN_LIFETIME_MILLIS = 10 * 60 * 1000L

fun Route.jwtDemoRoutes() {
    route("/demo-auth") {
        // Unprotected: mints a token. A real login would verify a password here.
        post("/token") {
            val user = call.request.queryParameters["user"]?.takeIf { it.isNotBlank() }
                ?: throw ApiException.BadRequest("user query parameter required")

            val token = JWT.create()
                .withIssuer(JWT_ISSUER)
                .withAudience(JWT_AUDIENCE)
                .withClaim("user", user)
                .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_LIFETIME_MILLIS))
                .sign(demoAlgorithm)

            call.respond(
                mapOf(
                    "token" to token,
                    "usage" to "Authorization: Bearer <token>",
                    "expires_in_seconds" to TOKEN_LIFETIME_MILLIS / 1000,
                ),
            )
        }

        // Anyone can read a JWT's claims — signing is integrity, not confidentiality.
        get("/decode") {
            val raw = call.request.queryParameters["token"]
                ?: throw ApiException.BadRequest("token query parameter required")
            val decoded = runCatching { JWT.decode(raw) }
                .getOrElse { throw ApiException.BadRequest("not a well-formed JWT") }

            call.respond(
                mapOf(
                    "header_alg" to decoded.algorithm,
                    "issuer" to decoded.issuer,
                    "audience" to decoded.audience,
                    "user_claim" to decoded.getClaim("user").asString(),
                    "expires_at" to decoded.expiresAt?.toInstant()?.toString(),
                    "note" to "decoded WITHOUT the secret — a JWT is signed, not encrypted",
                    "what_signing_proves" to "the token was not altered; it says nothing about who is reading it",
                ),
            )
        }

        // Protected by the named provider installed in plugins/Authentication.kt.
        authenticate("demo-jwt") {
            get("/whoami") {
                val principal = call.principal<JWTPrincipal>()
                call.respond(
                    mapOf(
                        "user" to principal?.getClaim("user", String::class),
                        "expires_at" to principal?.expiresAt?.toInstant()?.toString(),
                        "why_this_is_a_demo" to
                            "the real /admin routes are gated by Cloudflare Access before Ktor sees them",
                    ),
                )
            }
        }
    }
}
