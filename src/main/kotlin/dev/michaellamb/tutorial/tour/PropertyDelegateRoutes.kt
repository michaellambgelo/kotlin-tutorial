package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Kotlin: writing your own property delegate.
// /tour/properties uses the stdlib's delegates (`by lazy`, `by Delegates.observable`) and
// /tour/delegation covers *class* delegation. This is the mechanism underneath both: `by` is not a
// keyword with a fixed list of right-hand sides. Any object works, provided it supplies
//
//     operator fun getValue(thisRef: R, property: KProperty<*>): T
//     operator fun setValue(thisRef: R, property: KProperty<*>, value: T)   // for `var`
//
// The compiler rewrites the property's accessors into calls on that object. It is the same operator
// convention as /tour/operators — `by` resolves getValue the way `+` resolves plus. Note the
// delegate is handed the KProperty, so it knows the property's own name without being told; that is
// how a map-backed delegate looks up the right key, and it's why plugins/Dependencies.kt can write
// `val repo: NoteRepository by dependencies` with no string key at all.

/** Clamps every write into a range. The delegate enforces an invariant the type alone cannot. */
private class Clamped(private var value: Int, private val range: IntRange) : ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        this.value = value.coerceIn(range)
    }
}

/** Records every write, using the property's own name — supplied by the compiler, not by us. */
private class Audited<T>(private var value: T) : ReadWriteProperty<Any?, T> {
    val log = mutableListOf<String>()
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        log += "${property.name}: ${this.value} -> $value"
        this.value = value
    }
}

private class Settings {
    private val volumeDelegate = Clamped(5, 0..10)
    private val themeDelegate = Audited("dark")

    var volume: Int by volumeDelegate
    var theme: String by themeDelegate

    val themeChanges: List<String> get() = themeDelegate.log
}

// A map-backed delegate: the stdlib defines getValue on Map, so any Map is a valid delegate. The
// property name becomes the key, which is what makes this read so cleanly.
private class MapBackedConfig(source: Map<String, Any?>) {
    val host: String by source
    val port: Int by source
}

fun Route.propertyDelegateRoutes() {
    get("/property-delegates") {
        val settings = Settings()
        settings.volume = 99 // clamped to 10
        val clampedHigh = settings.volume
        settings.volume = -4 // clamped to 0
        val clampedLow = settings.volume

        settings.theme = "light"
        settings.theme = "solarized"

        val config = MapBackedConfig(mapOf("host" to "localhost", "port" to 8080))

        call.respond(
            mapOf(
                "clamped_after_setting_99" to clampedHigh,
                "clamped_after_setting_minus_4" to clampedLow,
                "audit_log" to settings.themeChanges, // delegate knew the property was named "theme"
                "map_backed_host" to config.host,
                "map_backed_port" to config.port,
                "mechanism" to mapOf(
                    "by" to "rewrites the accessors into getValue/setValue on the delegate object",
                    "KProperty" to "the delegate receives the property's metadata, including its name",
                    "stdlib_examples" to listOf("lazy", "Delegates.observable", "Map", "Ktor's `by dependencies`"),
                ),
            ),
        )
    }
}
