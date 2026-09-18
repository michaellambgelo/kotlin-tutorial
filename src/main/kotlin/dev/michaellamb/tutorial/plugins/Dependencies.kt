/*
 * Application wiring via Ktor's DI plugin.
 *
 * Pedagogical focus: **a property delegate you did not write**. `/tour/properties` shows `by lazy`
 * and `by Delegates.observable`; `/tour/delegation` shows *class* delegation. This is the third
 * case — an arbitrary object on the right of `by`, supplying values through the same
 * `getValue` operator convention:
 *
 *     val repo: NoteRepository by dependencies
 *
 * `dependencies` is not a map and not a magic keyword. It is an object that defines `getValue`, so
 * the compiler rewrites the property's getter into a call on it. Combined with a reified type
 * parameter (see /tour/generics), the *declared type of the property* is what selects the
 * dependency — there is no string key and no cast.
 *
 * The trade this replaces: `configureRouting` used to construct all four collaborators inline. That
 * was easy to read in one place but meant the route tree owned the lifecycle of a database
 * connection and an HTTP client. Registering them here separates "what exists" from "what uses it";
 * the cost is that a missing registration is now a startup failure rather than a compile error.
 */
package dev.michaellamb.tutorial.plugins

import dev.michaellamb.tutorial.notes.NoteRepository
import dev.michaellamb.tutorial.notes.NotesDatabase
import dev.michaellamb.tutorial.projects.ProjectRepository
import dev.michaellamb.tutorial.projects.ProjectsDatabase
import dev.michaellamb.tutorial.widgets.WidgetCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies

fun Application.configureDependencies() {
    dependencies {
        provide<NoteRepository> { NoteRepository(NotesDatabase.connect()) }
        // seedDefaults() is blocking and idempotent — it inserts the starting rows on first run.
        provide<ProjectRepository> {
            ProjectRepository(ProjectsDatabase.connect()).apply { seedDefaults() }
        }
        // One shared client for every outbound widget fetch; creating one per call would leak
        // connection pools.
        provide<HttpClient> { HttpClient(CIO) }
        provide<WidgetCache> { WidgetCache() }
    }
}
