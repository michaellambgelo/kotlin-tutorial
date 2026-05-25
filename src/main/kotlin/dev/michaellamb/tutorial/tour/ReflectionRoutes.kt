package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.reflect.full.memberProperties

// Kotlin: reflection (kotlin-reflect).
// `::class` yields a KClass describing the type at runtime. `memberProperties` walks its
// properties, and each KProperty1 can `.get(instance)` to read a value reflectively —
// without knowing the field names at compile time. `Droid::model` is a callable reference.
data class Droid(val id: String, val model: String)

fun Route.reflectionRoutes() {
    get("/reflection") {
        val r2 = Droid(id = "R2-D2", model = "astromech")
        val kClass = Droid::class

        // Read every property name → value reflectively.
        val properties = kClass.memberProperties.associate { it.name to it.get(r2) }

        // A callable (property) reference, invoked via reflection.
        val modelViaRef = Droid::model.get(r2)

        call.respond(
            mapOf(
                "class_name" to kClass.simpleName,
                "is_data_class" to kClass.isData,
                "properties" to properties,
                "model_via_callable_ref" to modelViaRef,
            )
        )
    }
}
