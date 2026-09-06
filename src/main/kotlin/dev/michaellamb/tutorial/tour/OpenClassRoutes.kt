package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: open and abstract classes.
// Classes are final by default — a subclass is only possible if the parent opts in with
// `open`, and a member is only overridable if it is `open` too. An `abstract` class is
// implicitly open and may leave members without a body for children to fill in. `super`
// reaches the parent implementation you just overrode.
private abstract class Vehicle(val name: String, val wheels: Int) {
    abstract fun sound(): String // no body: every child must supply one

    // `open`, so a child may replace it; without `open` this would be final.
    open fun describe(): String = "$name has $wheels wheels and goes ${sound()}"
}

private open class Car(name: String) : Vehicle(name, wheels = 4) {
    override fun sound() = "vroom"
}

private class RaceCar(name: String) : Car(name) {
    override fun sound() = "VROOOOM"

    // Overriding while still reusing the inherited implementation.
    override fun describe(): String = super.describe() + " (at speed)"
}

private class Motorcycle(name: String) : Vehicle(name, wheels = 2) {
    override fun sound() = "brap"
}

fun Route.openClassRoutes() {
    get("/open-classes") {
        val fleet: List<Vehicle> = listOf(Car("Sedan"), RaceCar("Formula"), Motorcycle("Scrambler"))

        call.respond(
            mapOf(
                "descriptions" to fleet.map { it.describe() },
                // The abstract parent's inherited describe() calls the child's sound().
                "sounds" to fleet.associate { it.name to it.sound() },
                "wheels" to fleet.associate { it.name to it.wheels },
                // Two levels of inheritance: RaceCar : Car : Vehicle.
                "race_car_is_a_car" to (fleet[1] is Car),
                "motorcycle_is_a_car" to (fleet[2] is Car),
            )
        )
    }
}
