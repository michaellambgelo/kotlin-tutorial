/*
 * HTTP-level caching and compression.
 *
 * Pedagogical focus: the same idea at two layers. `widgets/WidgetCache` memoises a rendered fragment
 * for 60s *inside* the process (saving work); CachingHeaders tells the browser it may skip the
 * request altogether for that same 60s (saving the round trip).
 *
 * ConditionalHeaders is the third layer and the one with a caveat worth knowing: it turns a request
 * into a 304 when the response *already carries* an ETag or Last-Modified — which Ktor supplies for
 * static file content, but not for a `respondText` built on the fly. Installing it does not
 * retroactively give every dynamic response a validator; earning a 304 for these widgets would mean
 * hashing the rendered fragment and setting the header explicitly. It is installed here because the
 * distinction between "a freshness lifetime" (Cache-Control) and "a validator" (ETag) is the point.
 *
 * Kotlin note: `when` over a nullable subject (see /tour/control-flow), returning `null` for "no
 * opinion", which is how Ktor's options provider signals "some other handler decides".
 */
package dev.michaellamb.tutorial.plugins

import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.content.CachingOptions
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders

private const val WIDGET_MAX_AGE_SECONDS = 60
private const val STATIC_MAX_AGE_SECONDS = 600

fun Application.configureHttp() {
    install(ConditionalHeaders)

    install(CachingHeaders) {
        options { call, outgoing ->
            val path = call.request.local.uri.substringBefore('?')
            when {
                // Never let a browser hold onto an admin form or a mutation's result.
                path.startsWith("/admin") -> CachingOptions(CacheControl.NoStore(null))
                // Matches WidgetCache's own 60s TTL — the blog embeds these on every page view.
                path.startsWith("/widgets/") ->
                    CachingOptions(CacheControl.MaxAge(WIDGET_MAX_AGE_SECONDS, visibility = CacheControl.Visibility.Public))
                outgoing.contentType?.withoutParameters() == ContentType.Text.CSS ->
                    CachingOptions(CacheControl.MaxAge(STATIC_MAX_AGE_SECONDS))
                else -> null // no opinion; let the response stand as-is
            }
        }
    }

    install(Compression) {
        gzip { priority = 1.0 }
        deflate { priority = 0.9 }
        // Below roughly one MTU, compressing costs more than it saves.
        minimumSize(1024)
    }
}
