package com.ingsisbry.snippet.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SnippetVersionTest {
    @Test
    fun `first should create initial version with version number one`() {
        val id = UUID.randomUUID()
        val snippetId = UUID.randomUUID()
        val createdAt = Instant.parse("2026-10-27T12:00:00Z")
        val content = "println(\"Hello, world!\")"

        val version =
            SnippetVersion.first(
                id = id,
                snippetId = snippetId,
                originalContent = content,
                createdAt = createdAt,
            )

        assertEquals(id, version.id)
        assertEquals(snippetId, version.snippetId)
        assertEquals(1, version.versionNumber)
        assertEquals(content, version.originalContent)
        assertNull(version.formattedContent)
        assertEquals(createdAt, version.createdAt)
    }
}
