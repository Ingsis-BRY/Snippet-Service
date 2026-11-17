package com.ingsisbry.snippet.adapter.web.mapper

import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.AlreadyShared
import com.ingsisbry.snippet.application.error.ContentMissing
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.error.PermissionServiceUnavailable
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetCorrupted
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.error.UserDirectoryUnavailable
import com.ingsisbry.snippet.support.SAMPLE_INVALID_CONTENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
                InvalidSnippetContent("UnexpectedToken", "Expected an expression", null) to HttpStatus.BAD_REQUEST,
                UnsupportedLanguage("Python", "3") to HttpStatus.BAD_REQUEST,
                LanguageServiceUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
                ContentStoreUnavailable("key", "down") to HttpStatus.SERVICE_UNAVAILABLE,
                PersistenceUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
                ContentMissing("key") to HttpStatus.INTERNAL_SERVER_ERROR,
                SnippetCorrupted(id, "missing version") to HttpStatus.INTERNAL_SERVER_ERROR,
                AccessDenied(id, "intruder") to HttpStatus.FORBIDDEN,
                AlreadyShared(id, "enzo") to HttpStatus.CONFLICT,
                PermissionServiceUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
                UserDirectoryUnavailable("down") to HttpStatus.SERVICE_UNAVAILABLE,
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
                PermissionServiceUnavailable("http://snippet-permission-service:8080 refused"),
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

    @Test
    fun `invalid content names the rule, its position and the engine's message`() {
        val problem = mapper.toResponse(SAMPLE_INVALID_CONTENT).body!!

        assertEquals("InvalidSnippetContent", problem.title)
        assertEquals(
            "Snippet breaks the rule 'UnexpectedToken' at line 1, column 17: Expected an expression",
            problem.detail,
        )
        assertEquals("UnexpectedToken", problem.properties?.get("rule"))
        assertEquals("Expected an expression", problem.properties?.get("message"))
        assertEquals(
            mapOf("start" to mapOf("line" to 1, "column" to 17), "end" to mapOf("line" to 1, "column" to 17)),
            problem.properties?.get("span"),
        )
    }

    @Test
    fun `invalid content without a position leaves the position out of the detail`() {
        val problem = mapper.toResponse(InvalidSnippetContent("NestingTooDeep", "Too deep", null)).body!!

        assertEquals("Snippet breaks the rule 'NestingTooDeep': Too deep", problem.detail)
        assertTrue(problem.properties!!.containsKey("span"))
        assertNull(problem.properties?.get("span"))
    }

    @Test
    fun `an unsupported language is titled as such`() {
        val problem = mapper.toResponse(UnsupportedLanguage("printscript", "9.9")).body!!

        assertEquals(HttpStatus.BAD_REQUEST.value(), problem.status)
        assertEquals("UnsupportedLanguage", problem.title)
        assertEquals("Language printscript 9.9 is not supported", problem.detail)
    }
}
