package dev.michaellamb.tutorial.tour

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Kotlin: interface delegation.
// `class Foo(bar: Bar) : Bar by bar` implements every member of Bar by forwarding to the
// given instance — the compiler writes the boilerplate. Override only what you want to
// change; everything else is inherited behavior without inheritance. Note the delegate is
// captured at construction, so an override is invisible to the delegate's own calls.
private interface DrawingTool {
    val color: String
    fun draw(shape: String): String
    fun erase(area: String): String
    fun info(): String
}

private class PenTool(override val color: String = "black") : DrawingTool {
    override fun draw(shape: String) = "drawing $shape in $color"
    override fun erase(area: String) = "erasing $area"
    override fun info() = "PenTool(color=$color)"
}

// Delegates draw()/erase()/info() to `base`; only `color` and `draw` are re-stated here.
private class RedPen(private val base: DrawingTool = PenTool()) : DrawingTool by base {
    override val color = "red"
    override fun draw(shape: String) = "drawing $shape in $color"
}

fun Route.delegationRoutes() {
    get("/delegation") {
        val pen = PenTool()
        val red = RedPen()

        call.respond(
            mapOf(
                "pen_draw" to pen.draw("circle"),
                "red_draw_overridden" to red.draw("circle"),
                "red_erase_delegated" to red.erase("corner"), // never written in RedPen
                // The delegate object was built with its own color, so its info() still says black:
                // delegation forwards calls, it does not rewire the delegate's `this`.
                "red_info_from_delegate" to red.info(),
                "red_color_property" to red.color,
            )
        )
    }
}
