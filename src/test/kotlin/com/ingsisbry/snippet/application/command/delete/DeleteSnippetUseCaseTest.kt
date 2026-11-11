package com.ingsisbry.snippet.application.command.delete

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DeleteSnippetUseCaseTest {
    @Mock
    private lateinit var snippetRepository: SnippetRepository

    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    private lateinit var useCase: DeleteSnippetUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            DeleteSnippetUseCase(
                snippetRepository = snippetRepository,
                contentStorage = contentStorage,
            )
    }

    @Test
    fun `execute should delete snippet and its content`() {
        val snippetId = UUID.randomUUID()
        val contentKeys =
            listOf(
                "snippets/$snippetId/versions/version-1/original",
                "snippets/$snippetId/versions/version-2/original",
            )

        Mockito
            .`when`(snippetRepository.deleteById(snippetId))
            .thenReturn(Result.Success(contentKeys))
        contentKeys.forEach { key ->
            Mockito
                .`when`(contentStorage.delete(key))
                .thenReturn(Result.Success(Unit))
        }

        val result = useCase.execute(snippetId)

        assertEquals(Result.Success(Unit), result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verify(contentStorage).delete(contentKeys[0])
        Mockito.verify(contentStorage).delete(contentKeys[1])
    }

    @Test
    fun `execute should fail with not found when snippet does not exist`() {
        val snippetId = UUID.randomUUID()
        val failure = Result.Failure(SnippetNotFound(snippetId))

        Mockito
            .`when`(snippetRepository.deleteById(snippetId))
            .thenReturn(failure)

        val result = useCase.execute(snippetId)

        assertEquals(failure, result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verifyNoInteractions(contentStorage)
    }

    @Test
    fun `execute should still succeed when deleting content fails`() {
        val snippetId = UUID.randomUUID()
        val contentKey = "snippets/$snippetId/versions/version-1/original"

        Mockito
            .`when`(snippetRepository.deleteById(snippetId))
            .thenReturn(Result.Success(listOf(contentKey)))
        Mockito
            .`when`(contentStorage.delete(contentKey))
            .thenReturn(Result.Failure(ContentStoreUnavailable(contentKey, "down")))

        val result = useCase.execute(snippetId)

        assertEquals(Result.Success(Unit), result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verify(contentStorage).delete(contentKey)
    }
}
