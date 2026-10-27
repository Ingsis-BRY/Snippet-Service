package com.ingsisbry.snippet.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SnippetTest {
    @Test
    fun `create should initialize snippet with the provided values`() {
        val id = UUID.randomUUID()
        val versionId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-27T12:00:00Z")
        val content = "fun main() = println(\"Hello, world!\")"

        val snippet =
            Snippet.create(
                id = id,
                name = "Hello World",
                description = "A simple Kotlin snippet",
                language = "Kotlin",
                languageVersion = "2.3",
                owner = "test-user",
                content = content,
                versionId = versionId,
                createdAt = createdAt,
            )

        assertEquals(id, snippet.id)
        assertEquals("Hello World", snippet.name)
        assertEquals("A simple Kotlin snippet", snippet.description)
        assertEquals("Kotlin", snippet.language)
        assertEquals("2.3", snippet.languageVersion)
        assertEquals("test-user", snippet.owner)

        assertEquals(versionId, snippet.currentVersion.id)
        assertEquals(id, snippet.currentVersion.snippetId)
        assertEquals(1, snippet.currentVersion.versionNumber)
        assertEquals(content, snippet.currentVersion.originalContent)
        assertNull(snippet.currentVersion.formattedContent)
        assertEquals(createdAt, snippet.currentVersion.createdAt)
    }
}
