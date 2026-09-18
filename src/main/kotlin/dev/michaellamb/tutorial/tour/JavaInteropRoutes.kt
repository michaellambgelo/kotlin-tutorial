package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.Optional

// Kotlin: calling Java, and being called from Java.
// This is where Kotlin's null safety meets its limit. Kotlin knows `String` is non-null and
// `String?` may be null — but a Java method has no such annotation, so Kotlin gives its return a
// PLATFORM TYPE, written `String!`. A platform type is "I don't know": Kotlin will let you treat it
// as either, and skips the compile-time check. That is the one hole in the type system, and it is
// deliberate — the alternative was forcing `?` on every Java call.
//
// Practically: assign a platform type to an explicitly-typed local as soon as it enters Kotlin. If
// you declare `val s: String = javaCall()` you get an immediate, well-located NPE when it's null;
// if you let it stay `String!` and carry it three layers deeper, you get one somewhere useless.
//
// The other direction is annotations that shape the bytecode Java sees: @JvmStatic puts a method on
// the class instead of the Companion object, @JvmOverloads generates the telescoping overloads Java
// needs because it has no default arguments, @JvmName renames, @JvmField drops the getter.
private class LegacyBridge {
    companion object {
        // Without @JvmStatic, Java must write LegacyBridge.Companion.describe().
        @JvmStatic
        fun describe(): String = "callable from Java as LegacyBridge.describe()"
    }
}

// Java has no default arguments. @JvmOverloads generates greet(String), greet(String, String) and
// greet(String, String, Int) so a Java caller isn't forced to pass all three.
@JvmOverloads
private fun greet(name: String, greeting: String = "Hello", times: Int = 1): String =
    (1..times).joinToString(" ") { "$greeting, $name!" }

fun Route.javaInteropRoutes() {
    get("/java-interop") {
        // System.getenv returns String! — a platform type. Kotlin permits BOTH of these lines;
        // only one of them is safe.
        val unsafeStyle: String? = System.getenv("DEFINITELY_NOT_SET") // handled as nullable
        val viaElvis: String = System.getenv("DEFINITELY_NOT_SET") ?: "(unset)"

        // Java collections arrive as platform types too; the mapping is to Kotlin's own interfaces.
        val javaList: List<String> = java.util.Arrays.asList("a", "b", "c")

        // Optional is Java's answer to the same problem. Kotlin's is the type system, which is why
        // idiomatic Kotlin unwraps an Optional at the boundary rather than passing it around.
        val optional: Optional<String> = Optional.of("present")
        val unwrapped: String? = optional.orElse(null)

        // Kotlin's String is java.lang.String at runtime — there is no boxing or wrapper here.
        val runtimeClass = "kotlin string".javaClass.name

        call.respond(
            mapOf(
                "platform_type_explained" to
                    "System.getenv() is String! — Kotlin skips the null check, so the NPE lands wherever you finally use it",
                "unsafe_style_was_null" to (unsafeStyle == null),
                "elvis_default" to viaElvis,
                "java_list" to javaList,
                "optional_unwrapped" to unwrapped,
                "kotlin_string_runtime_class" to runtimeClass, // java.lang.String
                "int_runtime_class" to 1.javaClass.name, // int — primitive, not boxed
                "boxed_int_runtime_class" to (1 as Any).javaClass.name, // java.lang.Integer
                "jvm_static" to LegacyBridge.describe(),
                "jvm_overloads_default" to greet("Kodee"),
                "jvm_overloads_explicit" to greet("Kodee", "Howdy", 2),
                "annotations" to listOf(
                    "@JvmStatic — real static method instead of Companion.INSTANCE",
                    "@JvmOverloads — generate telescoping overloads for Java's missing defaults",
                    "@JvmName — rename the JVM symbol (also escapes signature clashes)",
                    "@JvmField — expose the backing field directly, no getter",
                    "@Throws — emit a checked-exception signature Java can catch",
                ),
            ),
        )
    }
}
