package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: context parameters (2.2 preview — needs -Xcontext-parameters, see build.gradle.kts).
// The problem they solve: something is needed by a whole call tree but is not what any function is
// *about*. A logger, a tenant, a transaction, a request id. The usual options are both bad —
// thread a parameter through every signature, or reach for a global/ThreadLocal.
//
// A context parameter is declared with `context(name: T)` and is supplied IMPLICITLY at the call
// site from whatever is in scope. So the dependency stays explicit in the signature (unlike a
// global) without being restated at every call (unlike a parameter).
//
// Contrast with /tour/lambdas-with-receiver: a receiver puts ONE type into scope as `this` and the
// function becomes a member-like call on it. Context parameters are named, and you may have several
// — and crucially the function is still called normally, not on a receiver. That is the fix for the
// old context *receivers* design, where multiple anonymous `this`es made code unreadable.

private class RequestContext(val requestId: String, val user: String)

private class AuditLog {
    val lines = mutableListOf<String>()
    fun record(text: String) { lines += text }
}

// Needs both a RequestContext and an AuditLog — neither is an argument, both are in scope.
context(ctx: RequestContext, audit: AuditLog)
private fun publish(title: String): String {
    audit.record("[${ctx.requestId}] ${ctx.user} published '$title'")
    return "published '$title' as ${ctx.user}"
}

// Contexts propagate: this declares the same requirements and calls publish without passing them.
context(ctx: RequestContext, audit: AuditLog)
private fun publishAll(titles: List<String>): List<String> = titles.map { publish(it) }

// A context parameter can be an interface, so the *capability* is what's required, not a class.
private interface Clock { fun nowMillis(): Long }

context(clock: Clock)
private fun stamp(label: String): String = "$label@${clock.nowMillis()}"

fun Route.contextParameterRoutes() {
    get("/context-parameters") {
        val ctx = RequestContext(requestId = "req-42", user = "kodee")
        val audit = AuditLog()

        // `with` puts values into scope; the compiler matches them to the context parameters by
        // type. Nothing is passed explicitly to publish/publishAll.
        val results = with(ctx) {
            with(audit) {
                publishAll(listOf("first post", "second post"))
            }
        }

        val fixedClock = object : Clock {
            override fun nowMillis(): Long = 1_700_000_000_000
        }
        val stamped = with(fixedClock) { stamp("build") }

        call.respond(
            mapOf(
                "results" to results,
                "audit_log" to audit.lines, // written by publish() without ever receiving the log
                "stamped" to stamped,
                "why" to "a dependency every function in a call tree needs, but that none of them are about",
                "vs_receiver" to mapOf(
                    "context(name: T)" to "named, several allowed, function still called normally",
                    "T.() -> Unit receiver" to "exactly one, anonymous `this`, call looks like a member",
                ),
                "status" to "preview in Kotlin 2.2 — opt in with -Xcontext-parameters; replaced -Xcontext-receivers",
            ),
        )
    }
}
