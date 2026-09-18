package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: operator overloading.
// `a + b` is not built-in syntax for user types — it compiles to `a.plus(b)`. Mark a function
// `operator` and the compiler will accept the symbol for it. The name decides the symbol, so the
// set is fixed: plus/minus/times/div/rem, get/set (`[]`), invoke (`()`), contains (`in`),
// compareTo (`<` `>` `<=` `>=`), rangeTo (`..`), unaryMinus, inc/dec.
//
// Two are worth singling out. `compareTo` gives you all four comparison symbols from one function.
// And `equals` is why `==` on a data class compares contents: `==` compiles to `a?.equals(b) ?: (b
// === null)`, so it is null-safe *and* overridable — `===` is the one that always means identity.
private data class Money(val cents: Long, val currency: String = "USD") : Comparable<Money> {

    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "cannot add $currency to ${other.currency}" }
        return Money(cents + other.cents, currency)
    }

    operator fun minus(other: Money): Money = this + Money(-other.cents, other.currency)

    // Scaling by a count, not by another Money — operators need not be symmetric in type.
    operator fun times(factor: Int): Money = Money(cents * factor, currency)

    // Unary: `-price`
    operator fun unaryMinus(): Money = Money(-cents, currency)

    // One function, four symbols: < > <= >=
    override fun compareTo(other: Money): Int {
        require(currency == other.currency) { "cannot compare $currency to ${other.currency}" }
        return cents.compareTo(other.cents)
    }

    override fun toString(): String = "%s%.2f".format(if (cents < 0) "-" else "", kotlin.math.abs(cents) / 100.0) + " $currency"
}

// `get`/`set` give you indexing; `contains` gives you `in`; `invoke` makes the object callable.
private class Cart {
    private val lines = mutableMapOf<String, Money>()

    operator fun get(item: String): Money = lines[item] ?: Money(0)
    operator fun set(item: String, price: Money) { lines[item] = price }
    operator fun contains(item: String): Boolean = item in lines

    // `cart()` — an object you can call like a function.
    operator fun invoke(): Money = lines.values.fold(Money(0)) { acc, m -> acc + m }

    fun items(): Map<String, String> = lines.mapValues { it.value.toString() }
}

fun Route.operatorRoutes() {
    get("/operators") {
        val coffee = Money(450)
        val pastry = Money(325)

        val cart = Cart()
        cart["coffee"] = coffee // set
        cart["pastry"] = pastry // set

        val subtotal = cart() // invoke
        val tip = coffee * 2 // times
        val change = Money(2000) - subtotal // minus

        // Structural vs referential equality — the distinction `==` hides.
        val a = Money(450)
        val b = Money(450)

        call.respond(
            mapOf(
                "cart_items" to cart.items(),
                "subtotal" to subtotal.toString(), // via operator invoke
                "coffee_price" to cart["coffee"].toString(), // via operator get
                "has_pastry" to ("pastry" in cart), // via operator contains
                "has_tea" to ("tea" in cart),
                "double_coffee" to tip.toString(), // via operator times
                "change_from_20" to change.toString(), // via operator minus
                "negated" to (-coffee).toString(), // via operator unaryMinus
                "coffee_dearer_than_pastry" to (coffee > pastry), // via operator compareTo
                "cheapest" to minOf(coffee, pastry).toString(), // Comparable, so stdlib works too
                "sorted" to listOf(coffee, pastry, Money(100)).sorted().map { it.toString() },
                "structural_equality_==" to (a == b), // true — data class equals compares contents
                "referential_equality_===" to (a === b), // false — two distinct instances
            ),
        )
    }
}
