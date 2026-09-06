package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: lambda expressions with receiver (function literals with receiver).
// A parameter typed `Menu.() -> Unit` is a lambda whose `this` is a Menu, so the block
// can call the receiver's members unqualified. That is the whole trick behind Kotlin's
// type-safe builders — kotlinx.html, Gradle's KTS, and Ktor's own `routing { }` are all
// this same shape.
private class MenuItem(val name: String, val priceCents: Int)

private class Menu(val name: String) {
    val items = mutableListOf<MenuItem>()

    // Called with no receiver prefix inside the builder block below.
    fun item(name: String, priceCents: Int) {
        items.add(MenuItem(name, priceCents))
    }
}

// `init` is the lambda with receiver: it runs *on* the freshly built Menu.
private fun menu(name: String, init: Menu.() -> Unit): Menu = Menu(name).apply(init)

fun Route.lambdaReceiverRoutes() {
    get("/lambdas-with-receiver") {
        val breakfast = menu("Breakfast") {
            // `this` is the Menu — item() needs no qualifier.
            item("Coffee", 350)
            item("Pancakes", 900)
            item("Omelette", 1100)
        }

        call.respond(
            mapOf(
                "menu" to breakfast.name,
                "items" to breakfast.items.map { mapOf("name" to it.name, "price_cents" to it.priceCents) },
                "total_cents" to breakfast.items.sumOf { it.priceCents },
                // The same idea one level down: buildString's block receives a StringBuilder.
                "build_string_receiver" to buildString {
                    append(breakfast.name)
                    append(": ")
                    append(breakfast.items.joinToString { it.name })
                },
            )
        )
    }
}
