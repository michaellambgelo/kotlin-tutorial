package dev.michaellamb.tutorial.projects

import dev.michaellamb.tutorial.module
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRoutesTest {

    // Point BOTH stores at throwaway DBs so module() (which connects notes + projects and seeds the
    // default projects) never touches the real build/*.db. Set before module() runs.
    @TempDir
    lateinit var tmp: File

    @BeforeTest
    fun setDbPaths() {
        System.setProperty("projects.db.path", File(tmp, "projects-${UUID.randomUUID()}.db").absolutePath)
        System.setProperty("notes.db.path", File(tmp, "notes-${UUID.randomUUID()}.db").absolutePath)
    }

    @AfterTest
    fun clearDbPaths() {
        System.clearProperty("projects.db.path")
        System.clearProperty("notes.db.path")
    }

    @Test
    fun `widget renders seeded projects as an HTML fragment`() = testApplication {
        application { module() }
        val resp = client.get("/widgets/projects")
        assertEquals(HttpStatusCode.OK, resp.status)
        val html = resp.bodyAsText()
        assertTrue(html.contains("projects-widget"), "is the projects widget fragment")
        assertTrue(html.contains("letterboxd.michaellamb.dev"), "shows a seeded project")
        assertTrue(html.contains("tech-pill"), "renders tech tags")
    }

    @Test
    fun `admin page renders the form and current projects`() = testApplication {
        application { module() }
        val resp = client.get("/admin/projects")
        assertEquals(HttpStatusCode.OK, resp.status)
        val html = resp.bodyAsText()
        assertTrue(html.contains("Projects — admin"))
        assertTrue(html.contains("Add project"))
        assertTrue(html.contains("boxd-card.michaellamb.dev"), "lists a seeded project")
    }

    @Test
    fun `admin create persists a project and it shows on the page`() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        val create = client.post("/admin/projects") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                listOf(
                    "name" to "demo.michaellamb.dev",
                    "url" to "https://demo.michaellamb.dev",
                    "description" to "Spring Boot sandbox",
                    "tech" to "Java, Spring Boot",
                ).formUrlEncode(),
            )
        }
        assertEquals(HttpStatusCode.Found, create.status, "create redirects after the POST")

        val page = client.get("/admin/projects").bodyAsText()
        assertTrue(page.contains("demo.michaellamb.dev"), "created project appears in the admin list")

        // and it surfaces on the public widget too
        val widget = client.get("/widgets/projects").bodyAsText()
        assertTrue(widget.contains("demo.michaellamb.dev"))
    }

    @Test
    fun `admin create with a blank name is rejected`() = testApplication {
        application { module() }
        val client = createClient { followRedirects = false }

        client.post("/admin/projects") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("name" to "", "url" to "https://rejected.example").formUrlEncode())
        }

        // toProjectInput() returned null for the blank name, so nothing was persisted
        val admin = client.get("/admin/projects").bodyAsText()
        assertTrue(!admin.contains("rejected.example"), "blank-name submission is not saved")
    }
}
