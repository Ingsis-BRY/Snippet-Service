package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DownloadSnippetUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    private lateinit var useCase: DownloadSnippetUseCase

    private val snippetId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val originalContent = "fun main() = println(\"Hello\")"

    @BeforeEach
    fun setUp() {
        useCase = DownloadSnippetUseCase(snippetQueryRepository)
    }

    @Test
    fun `execute should return original content when variant is ORIGINAL`() {
        val snippet = createSnippet()

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(snippet)

        val result =
            useCase.execute(
                snippetId,
                SnippetContentVariant.ORIGINAL,
            )

        assertEquals(originalContent, result)
        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should return content when variant is FORMATTED`() {
        val snippet = createSnippet()

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(snippet)

        val result =
            useCase.execute(
                snippetId,
                SnippetContentVariant.FORMATTED,
            )

        // Actualmente, el caso de uso devuelve originalContent
        // también para la variante FORMATTED.
        assertEquals(originalContent, result)
        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should return null when snippet does not exist`() {
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(null)

        val result =
            useCase.execute(
                snippetId,
                SnippetContentVariant.ORIGINAL,
            )

        assertNull(result)
        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    private fun createSnippet(): Snippet {
        val version =
            SnippetVersion(
                id = versionId,
                snippetId = snippetId,
                versionNumber = 1,
                originalContent = originalContent,
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
