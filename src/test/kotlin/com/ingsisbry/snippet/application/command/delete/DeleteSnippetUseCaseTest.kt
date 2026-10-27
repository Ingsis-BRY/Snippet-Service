package com.ingsisbry.snippet.application.command.delete

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
            .thenReturn(contentKeys)

        val result = useCase.execute(snippetId)

        assertTrue(result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verify(contentStorage).delete(contentKeys[0])
        Mockito.verify(contentStorage).delete(contentKeys[1])
    }

    @Test
    fun `execute should return false when snippet does not exist`() {
        val snippetId = UUID.randomUUID()

        Mockito
            .`when`(snippetRepository.deleteById(snippetId))
            .thenReturn(null)

        val result = useCase.execute(snippetId)

        assertFalse(result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verifyNoInteractions(contentStorage)
    }

    @Test
    fun `execute should return true when deleting content fails`() {
        val snippetId = UUID.randomUUID()
        val contentKey = "snippets/$snippetId/versions/version-1/original"

        Mockito
            .`when`(snippetRepository.deleteById(snippetId))
            .thenReturn(listOf(contentKey))

        Mockito
            .doThrow(RuntimeException("Storage unavailable"))
            .`when`(contentStorage)
            .delete(contentKey)

        val result = useCase.execute(snippetId)

        assertTrue(result)

        Mockito.verify(snippetRepository).deleteById(snippetId)
        Mockito.verify(contentStorage).delete(contentKey)
    }
}
