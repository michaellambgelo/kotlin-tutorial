package dev.michaellamb.tutorial.tour

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.sqrt

// Kotlin: sealed interface + exhaustive when.
// A `sealed` hierarchy is a closed set of subtypes known at compile time. When `when`
// is used as an expression on a sealed type, the compiler verifies every subtype is
// handled — no `else` branch needed.
//
// The Jackson annotations drive runtime (de)serialization; the kotlinx @Serializable/@SerialName
// pair drives OpenAPI schema generation, producing a oneOf with a "type" discriminator that mirrors
// Jackson's. Both use the same discriminator property ("type") and subtype names. The generator
// omits the discriminator property from each subtype schema, so plugins/OpenApi.kt completes it
// (and adds a valid request example) — keeping Swagger's "Try it out" body deserializable.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Shape.Circle::class, name = "Circle"),
    JsonSubTypes.Type(value = Shape.Square::class, name = "Square"),
    JsonSubTypes.Type(value = Shape.Triangle::class, name = "Triangle"),
)
@Serializable
sealed interface Shape {
    @Serializable
    @SerialName("Circle")
    data class Circle(val radius: Double) : Shape

    @Serializable
    @SerialName("Square")
    data class Square(val side: Double) : Shape

    @Serializable
    @SerialName("Triangle")
    data class Triangle(val a: Double, val b: Double, val c: Double) : Shape
}

fun Shape.area(): Double = when (this) {
    is Shape.Circle -> PI * radius * radius
    is Shape.Square -> side * side
    is Shape.Triangle -> {
        val s = (a + b + c) / 2.0
        sqrt(s * (s - a) * (s - b) * (s - c))
    }
    // No `else` — adding a new Shape subtype would force this `when` to be updated.
}

fun Route.sealedWhenRoutes() {
    post("/sealed-when") {
        val shape = try {
            call.receive<Shape>()
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid body")))
            return@post
        }
        call.respond(mapOf("shape" to shape, "area" to shape.area()))
    }
}
