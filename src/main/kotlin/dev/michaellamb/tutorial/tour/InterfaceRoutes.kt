package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: interfaces + polymorphism.
// An interface can declare abstract members (`name`, `sound()`) and supply a default
// implementation (`describe()`) built on them. Each implementer overrides only what's
// abstract; iterating a List<Animal> dispatches `sound()` dynamically to the real type.
private interface Animal {
    val name: String
    fun sound(): String
    fun describe(): String = "$name says ${sound()}" // default method
}

private class Dog(override val name: String) : Animal {
    override fun sound() = "Woof"
}

private class Cat(override val name: String) : Animal {
    override fun sound() = "Meow"
}

private class Cow(override val name: String) : Animal {
    override fun sound() = "Moo"
    override fun describe() = "$name the cow goes ${sound()}" // overrides the default too
}

fun Route.interfaceRoutes() {
    get("/interfaces") {
        val animals: List<Animal> = listOf(Dog("Rex"), Cat("Milo"), Cow("Bessie"))

        call.respond(
            mapOf(
                // Same call site, three runtime types — dynamic dispatch.
                "descriptions" to animals.map { it.describe() },
                "sounds" to animals.associate { it.name to it.sound() },
            )
        )
    }
}
