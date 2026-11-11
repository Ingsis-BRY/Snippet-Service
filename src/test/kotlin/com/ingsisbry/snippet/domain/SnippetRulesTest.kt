package com.ingsisbry.snippet.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SnippetRulesTest {
    private fun violation(
        name: String = "name",
        description: String = "description",
        language: String = "Kotlin",
        languageVersion: String = "2.3",
        content: String = "content",
    ) = SnippetRules.violation(name, description, language, languageVersion, content)

    @Test
    fun `valid fields have no violation`() {
        assertNull(violation())
    }

    @Test
    fun `blank description and empty content are allowed`() {
        assertNull(violation(description = "", content = ""))
    }

    @Test
    fun `blank required fields are rejected`() {
        assertEquals("name must not be blank", violation(name = " "))
        assertEquals("language must not be blank", violation(language = ""))
        assertEquals("languageVersion must not be blank", violation(languageVersion = "  "))
    }

    @Test
    fun `too long fields are rejected`() {
        val tooLong = "a".repeat(SnippetRules.MAX_TEXT_LENGTH + 1)

        assertEquals("name must be at most 255 characters", violation(name = tooLong))
        assertEquals("description must be at most 255 characters", violation(description = tooLong))
        assertEquals(
            "content must be at most 1000000 characters",
            violation(content = "a".repeat(SnippetRules.MAX_CONTENT_LENGTH + 1)),
        )
    }
}
