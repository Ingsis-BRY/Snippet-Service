package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
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
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet()))

        val result = useCase.execute(snippetId, SnippetContentVariant.ORIGINAL)

        assertEquals(Result.Success(originalContent), result)
        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should return content when variant is FORMATTED`() {
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet()))

        val result = useCase.execute(snippetId, SnippetContentVariant.FORMATTED)

        // Currently the use case returns the original content for the FORMATTED variant too.
        assertEquals(Result.Success(originalContent), result)
        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should propagate the error when the snippet cannot be loaded`() {
        val failure = Result.Failure(SnippetNotFound(snippetId))

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(failure)

        val result = useCase.execute(snippetId, SnippetContentVariant.ORIGINAL)

        assertEquals(failure, result)
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
