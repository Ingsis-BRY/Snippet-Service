package com.ingsisbry.snippet.application.command.cleanup

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.port.out.storage.StoredContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class DeleteOrphanedContentUseCaseTest {
    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    private lateinit var useCase: DeleteOrphanedContentUseCase

    private val now = Instant.parse("2026-11-11T12:00:00Z")
    private val cutoff = now.minus(DeleteOrphanedContentUseCase.GRACE_PERIOD)

    @BeforeEach
    fun setUp() {
        useCase =
            DeleteOrphanedContentUseCase(
                contentStorage = contentStorage,
                snippetQueryRepository = snippetQueryRepository,
                clock = Clock.fixed(now, ZoneOffset.UTC),
            )
    }

    @Test
    fun `deletes only old blobs that no snippet version references`() {
        val oldOrphan = StoredContent("old-orphan", cutoff.minusSeconds(1))
        val oldReferenced = StoredContent("old-referenced", cutoff.minusSeconds(1))
        val recentOrphan = StoredContent("recent-orphan", cutoff.plusSeconds(1))

        givenStored(oldOrphan, oldReferenced, recentOrphan)
        Mockito
            .`when`(snippetQueryRepository.findReferencedContentKeys(listOf("old-orphan", "old-referenced")))
            .thenReturn(setOf("old-referenced"))
        Mockito.`when`(contentStorage.delete("old-orphan")).thenReturn(Result.Success(Unit))

        val result = useCase.execute()

        assertEquals(Result.Success(1), result)
        Mockito.verify(contentStorage).delete("old-orphan")
        Mockito.verify(contentStorage, Mockito.never()).delete("old-referenced")
        Mockito.verify(contentStorage, Mockito.never()).delete("recent-orphan")
    }

    @Test
    fun `a blob exactly at the grace period boundary is still protected`() {
        givenStored(StoredContent("boundary", cutoff))

        val result = useCase.execute()

        assertEquals(Result.Success(0), result)
        Mockito.verifyNoInteractions(snippetQueryRepository)
        Mockito.verify(contentStorage, Mockito.never()).delete(Mockito.anyString())
    }

    @Test
    fun `does not query the database when every blob is within the grace period`() {
        givenStored(StoredContent("fresh", now))

        val result = useCase.execute()

        assertEquals(Result.Success(0), result)
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `keeps going and does not count blobs whose deletion fails`() {
        givenStored(
            StoredContent("a", cutoff.minusSeconds(1)),
            StoredContent("b", cutoff.minusSeconds(1)),
        )
        Mockito
            .`when`(snippetQueryRepository.findReferencedContentKeys(listOf("a", "b")))
            .thenReturn(emptySet())
        Mockito
            .`when`(contentStorage.delete("a"))
            .thenReturn(Result.Failure(ContentStoreUnavailable("a", "down")))
        Mockito.`when`(contentStorage.delete("b")).thenReturn(Result.Success(Unit))

        val result = useCase.execute()

        assertEquals(Result.Success(1), result)
        Mockito.verify(contentStorage).delete("a")
        Mockito.verify(contentStorage).delete("b")
    }

    @Test
    fun `fails without deleting anything when the blobs cannot be listed`() {
        val failure = Result.Failure(ContentStoreUnavailable("snippets", "down"))
        Mockito.`when`(contentStorage.listAll()).thenReturn(failure)

        val result = useCase.execute()

        assertEquals(failure, result)
        Mockito.verifyNoInteractions(snippetQueryRepository)
        Mockito.verify(contentStorage, Mockito.never()).delete(Mockito.anyString())
    }

    private fun givenStored(vararg stored: StoredContent) {
        Mockito.`when`(contentStorage.listAll()).thenReturn(Result.Success(stored.toList()))
    }
}
