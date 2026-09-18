package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: tailrec, and the nested/inner class distinction.
//
// `tailrec` is a compiler instruction, not a hint: if the function's recursive call is genuinely
// the LAST thing it does, the compiler rewrites it into a loop and the stack stops growing. If it
// isn't in tail position the compiler refuses to build — so `tailrec` is a checked claim, unlike a
// comment saying "this is tail recursive". Note `factorial(n) = n * factorial(n - 1)` is NOT tail
// recursive: the multiplication happens after the call returns. Threading an accumulator is how you
// convert one, which is what makes the rewrite possible.
//
// Separately: `class Node` nested inside another class is just namespacing — it holds no reference
// to the outer instance. Adding `inner` is what captures the enclosing instance and makes
// `this@Outer` available. Kotlin defaults to the non-capturing case; Java defaults to the opposite.

private tailrec fun sumTo(n: Long, acc: Long = 0): Long =
    if (n == 0L) acc else sumTo(n - 1, acc + n) // the call IS the whole expression -> becomes a loop

private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

// Not tailrec: the multiply happens after the recursive call returns, so the frame must survive.
private fun factorial(n: Long): Long = if (n <= 1) 1 else n * factorial(n - 1)

private class Chapter(val name: String) {

    // Nested: no reference to the Chapter that lexically contains it.
    class Metadata(val routeCount: Int) {
        fun describe(): String = "$routeCount routes"
    }

    // Inner: captures the Chapter instance, so `this@Chapter` resolves.
    inner class Entry(val path: String) {
        fun qualified(): String = "${this@Chapter.name}$path"
    }
}

fun Route.recursionRoutes() {
    get("/recursion") {
        // 100_000 frames would blow the default stack without the tailrec rewrite.
        val deepSum = sumTo(100_000)

        val stackOverflowAt = runCatching { factorial(100_000) }
            .fold(onSuccess = { "no overflow" }, onFailure = { it::class.simpleName ?: "error" })

        val chapter = Chapter("beyond")

        call.respond(
            mapOf(
                "sum_to_100000_via_tailrec" to deepSum,
                "same_by_formula" to (100_000L * 100_001L / 2), // proves the rewrite is correct
                "gcd_1071_462" to gcd(1071, 462),
                "non_tailrec_factorial_deep" to stackOverflowAt, // StackOverflowError
                "small_factorial" to factorial(20),
                "nested_class" to Chapter.Metadata(7).describe(), // built with no Chapter instance
                "inner_class" to chapter.Entry("/flow").qualified(), // needs one: beyond/flow
                "rules" to listOf(
                    "tailrec — the recursive call must be the last operation, or it fails to compile",
                    "nested (default) — no outer reference; construct it standalone",
                    "inner — captures the outer instance; only constructible from one",
                ),
            ),
        )
    }
}
