package dev.michaellamb.tutorial.tour

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

// The service pretty-prints its JSON. These tests assert on values, not whitespace, so
// responses are re-parsed and re-serialized compactly before being matched.
private val mapper = ObjectMapper()

internal suspend fun HttpResponse.json(): JsonNode = mapper.readTree(bodyAsText())

internal suspend fun HttpResponse.compactJson(): String = json().toString()
