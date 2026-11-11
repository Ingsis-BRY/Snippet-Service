package com.ingsisbry.snippet.application.query.get

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
            .thenReturn(Result.Success(snippet))

        val result = useCase.execute(query)

        assertEquals(Result.Success(snippet), result)

        Mockito.verify(snippetQueryRepository).findById(snippetId)
    }

    @Test
    fun `execute should fail with not found when snippet does not exist`() {
        val query = GetSnippetQuery(snippetId)
        val failure = Result.Failure(SnippetNotFound(snippetId))

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(failure)

        val result = useCase.execute(query)

        assertEquals(failure, result)

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
