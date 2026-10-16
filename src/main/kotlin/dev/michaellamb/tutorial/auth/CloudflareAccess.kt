/*
 * The admin forms' real gate, checked twice: Cloudflare Access at the edge, and here.
 *
 * Access stops anyone on the internet who hasn't passed the One-time-PIN policy. It can't stop a
 * request that never touches the edge — anyone on the LAN or wg0 can reach node5:8080 directly. So
 * every request Access forwards carries a signed `Cf-Access-Jwt-Assertion`, and the admin routes
 * verify it: RS256 against the team's published keys, issuer = the team domain, audience = the
 * Access application's AUD (the `admin.michaellamb.dev` app).
 *
 * This is NOT a second login scheme (see JwtDemo.kt for why that would be harmful): nobody signs in
 * here. It only checks that the edge's own token is present and genuine.
 *
 * Off when `CF_ACCESS_TEAM_DOMAIN` or `CF_ACCESS_AUD` is unset — local dev, where there is no edge.
 *
 * Kotlin note: [behindAccess] takes `build: Route.() -> Unit` — a lambda with receiver, the same
 * shape as Ktor's own `route { }` — so a call site reads like any other routing block whether or not
 * the check is switched on.
 */
package dev.michaellamb.tutorial.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.RSAKeyProvider
import dev.michaellamb.tutorial.errors.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import java.net.URI
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

const val CF_ACCESS_PROVIDER = "cf-access"
const val CF_ACCESS_HEADER = "Cf-Access-Jwt-Assertion"

class CloudflareAccess(val verifier: JWTVerifier) {
    companion object {
        /** `https://<team>.cloudflareaccess.com`, from a bare or https-prefixed team domain. */
        fun issuerFor(teamDomain: String): String =
            "https://" + teamDomain.trim().removePrefix("https://").trimEnd('/')

        fun fromEnv(
            teamDomain: String? = System.getenv("CF_ACCESS_TEAM_DOMAIN"),
            audience: String? = System.getenv("CF_ACCESS_AUD"),
        ): CloudflareAccess? {
            val team = teamDomain?.takeIf { it.isNotBlank() } ?: return null
            val aud = audience?.trim()?.takeIf { it.isNotBlank() } ?: return null
            val issuer = issuerFor(team)
            // The full URL, not the String overload: that one appends /.well-known/jwks.json, which
            // is not where Cloudflare publishes Access keys.
            val jwks = JwkProviderBuilder(URI("$issuer/cdn-cgi/access/certs").toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
            return CloudflareAccess(verifierFor(Algorithm.RSA256(JwksKeys(jwks)), issuer, aud))
        }

        fun verifierFor(algorithm: Algorithm, issuer: String, audience: String): JWTVerifier =
            JWT.require(algorithm).withIssuer(issuer).withAudience(audience).build()
    }
}

// A named class rather than an anonymous `object :` — the openApi compiler plugin crashes on an
// anonymous object declared inside a function ("Parent of element ... is not initialized").
private class JwksKeys(private val jwks: JwkProvider) : RSAKeyProvider {
    // null (not a throw) for an unknown kid or an unreachable JWKS: the verifier turns it into a
    // failed signature, which the challenge below answers with 403.
    override fun getPublicKeyById(keyId: String?): RSAPublicKey? =
        runCatching { jwks.get(keyId).publicKey as RSAPublicKey }.getOrNull()
    override fun getPrivateKey(): RSAPrivateKey? = null
    override fun getPrivateKeyId(): String? = null
}

/** Registers the `cf-access` provider: reads the Access header, verifies it, 403s on failure. */
fun AuthenticationConfig.cloudflareAccess(access: CloudflareAccess) {
    jwt(CF_ACCESS_PROVIDER) {
        // Access puts the token in its own header, not `Authorization: Bearer`.
        authHeader { call -> call.request.headers[CF_ACCESS_HEADER]?.let { HttpAuthHeader.Single("Bearer", it) } }
        verifier(access.verifier)
        validate { credential -> JWTPrincipal(credential.payload) }
        // 403, not Ktor's default 401: there's nothing to log in to here. Real users never see this —
        // the edge already sent them through Access. Only a request that skipped the edge lands here.
        challenge { _, _ ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("Cloudflare Access token missing or invalid"))
        }
    }
}

/** Mounts [build] behind the `cf-access` check, or unguarded when [access] is null (local dev). */
fun Route.behindAccess(access: CloudflareAccess?, build: Route.() -> Unit) {
    if (access == null) build() else authenticate(CF_ACCESS_PROVIDER, build = build)
}
