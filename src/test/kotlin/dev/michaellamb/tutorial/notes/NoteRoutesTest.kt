package dev.michaellamb.tutorial.notes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.michaellamb.tutorial.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteRoutesTest {

    private val mapper = ObjectMapper()

    @Test
    fun `full CRUD roundtrip`() = testApplication {
        application { module() }

        // initial list is empty
        client.get("/notes").let { resp ->
            assertEquals(HttpStatusCode.OK, resp.status)
            assertEquals("[ ]", resp.bodyAsText().trim())
        }

        // create
        val created = client.post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"first","body":"hello"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdNode = mapper.readTree(created.bodyAsText()) as ObjectNode
        val id = createdNode.get("id").asText()
        assertTrue(id.isNotBlank())

        // get by id
        client.get("/notes/$id").let { resp ->
            assertEquals(HttpStatusCode.OK, resp.status)
            assertTrue(resp.bodyAsText().contains("\"first\""))
        }

        // update
        client.put("/notes/$id") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"updated","body":null}""")
        }.let { resp ->
            assertEquals(HttpStatusCode.OK, resp.status)
            assertTrue(resp.bodyAsText().contains("\"updated\""))
            assertTrue(resp.bodyAsText().contains("\"hello\""), "body should be preserved")
        }

        // delete
        client.delete("/notes/$id").let { resp ->
            assertEquals(HttpStatusCode.NoContent, resp.status)
        }

        // 404 after delete
        client.get("/notes/$id").let { resp ->
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }

    @Test
    fun `blank title is rejected`() = testApplication {
        application { module() }
        val response = client.post("/notes") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"","body":"x"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid id returns 400`() = testApplication {
        application { module() }
        val response = client.get("/notes/not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
