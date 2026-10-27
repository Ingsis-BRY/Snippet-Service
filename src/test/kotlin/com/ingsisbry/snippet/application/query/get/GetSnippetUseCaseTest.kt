package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GetSnippetUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    private lateinit var useCase: GetSnippetUseCase

    private val snippetId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = GetSnippetUseCase(snippetQueryRepository)
    }

    @Test
    fun `execute should return snippet when it exists`() {
        val snippet = createSnippet()
        val query = GetSnippetQuery(snippetId)

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(snippet)

        val result = useCase.execute(query)

        assertSame(snippet, result)
        assertEquals(snippetId, result.id)

        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should throw NoSuchElementException when snippet does not exist`() {
        val query = GetSnippetQuery(snippetId)

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(null)

        val exception =
            assertThrows(NoSuchElementException::class.java) {
                useCase.execute(query)
            }

        assertEquals(
            "Snippet $snippetId not found",
            exception.message,
        )

        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    private fun createSnippet(): Snippet {
        val version =
            SnippetVersion(
                id = UUID.randomUUID(),
                snippetId = snippetId,
                versionNumber = 1,
                originalContent = "fun main() = println(\"Hello\")",
                formattedContent = null,
                createdAt = Instant.parse("2026-10-27T12:00:00Z"),
            )

        return Snippet(
            id = snippetId,
            name = "Hello World",
            description = "Test snippet",
            language = "Kotlin",
            languageVersion = "2.3",
            owner = "test-user",
            currentVersion = version,
        )
    }
}
