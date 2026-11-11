package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.error.ContentMissing
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class SnippetErrorMapperTest {
    private val mapper = SnippetErrorMapper()
    private val id = UUID.randomUUID()

    @Test
    fun `each error is mapped to its http status`() {
        val expected: Map<SnippetError, HttpStatus> =
            mapOf(
                SnippetNotFound(id) to HttpStatus.NOT_FOUND,
                SnippetConflict(id) to HttpStatus.CONFLICT,
                InvalidInput("bad") to HttpStatus.BAD_REQUEST,
                InvalidSnippetContent("no-unused-vars", 3, 7) to HttpStatus.BAD_REQUEST,
                UnsupportedLanguage("Python", "3") to HttpStatus.BAD_REQUEST,
                LanguageServiceUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
                ContentStoreUnavailable("key", "down") to HttpStatus.SERVICE_UNAVAILABLE,
                PersistenceUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
                ContentMissing("key") to HttpStatus.INTERNAL_SERVER_ERROR,
                SnippetCorrupted(id, "missing version") to HttpStatus.INTERNAL_SERVER_ERROR,
            )

        expected.forEach { (error, status) ->
            val response = mapper.toResponse(error)

            assertEquals(status, response.statusCode, "status for $error")
            assertEquals(status.value(), response.body?.status, "problem status for $error")
        }
    }

    @Test
    fun `server errors do not leak internal details`() {
        val leaky =
            listOf(
                ContentStoreUnavailable("secret/blob/key", "connection string xyz"),
                PersistenceUnavailable("jdbc:postgresql://secret-host"),
                ContentMissing("secret/blob/key"),
                SnippetCorrupted(id, "secret reason"),
            )

        leaky.forEach { error ->
            val detail =
                mapper
                    .toResponse(error)
                    .body
                    ?.detail
                    .orEmpty()

            assertFalse(detail.contains("secret"), "detail leaks internals for $error: $detail")
            assertFalse(detail.contains("xyz"), "detail leaks internals for $error: $detail")
        }
    }

    @Test
    fun `invalid input exposes its reason to the client`() {
        val response = mapper.toResponse(InvalidInput("Size must be between 1 and 100"))

        assertEquals("Size must be between 1 and 100", response.body?.detail)
    }
}
