package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetRules
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.SAMPLE_INVALID_CONTENT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UpdateSnippetContentUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    @Mock
    private lateinit var snippetRepository: SnippetRepository

    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    private lateinit var useCase: UpdateSnippetContentUseCase

    private var contentValidation: Result<SnippetError, Unit> = Result.Success(Unit)

    private val now = Instant.parse("2026-11-11T10:00:00Z")

    private val permissions = FakeSnippetPermissions()

    @BeforeEach
    fun setUp() {
        useCase =
            UpdateSnippetContentUseCase(
                snippetQueryRepository,
                snippetRepository,
                contentStorage,
                SnippetContentValidator { _, _, _ -> contentValidation },
                permissions,
                Clock.fixed(now, ZoneOffset.UTC),
            )
    }

    @Test
    fun `execute denies the update without touching anything when the user cannot write`() {
        val id = UUID.randomUUID()
        permissions.allowed = false

        val result = useCase.execute(UpdateSnippetContentCommand(id, "reader", "x"))

        assertEquals(Result.Failure(AccessDenied(id, "reader")), result)
        Mockito.verifyNoInteractions(snippetQueryRepository, snippetRepository, contentStorage)
    }

    @Test
    fun `execute rejects content that breaks a language rule without touching storage or database`() {
        // given
        val id = UUID.randomUUID()
        Mockito
            .`when`(snippetQueryRepository.findMetadataById(id))
            .thenReturn(Result.Success(metadata(id)))
        contentValidation = Result.Failure(SAMPLE_INVALID_CONTENT)

        // when
        val result = useCase.execute(UpdateSnippetContentCommand(id, "test-user", "let x = 1;"))

        // then
        assertEquals(Result.Failure(SAMPLE_INVALID_CONTENT), result)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute creates a new version keeping the other fields`() {
        val id = UUID.randomUUID()
        Mockito
            .`when`(snippetQueryRepository.findMetadataById(id))
            .thenReturn(Result.Success(metadata(id)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.update(anySnippet(), Mockito.anyString()))
            .thenAnswer { Result.Success(it.getArgument<Snippet>(0)) }

        val result = useCase.execute(UpdateSnippetContentCommand(id, "test-user", "new content"))

        val snippet = (result as Result.Success).value
        assertEquals("Old name", snippet.name)
        assertEquals("Old description", snippet.description)
        assertEquals("Kotlin", snippet.language)
        assertEquals("2.2", snippet.languageVersion)
        assertEquals("test-user", snippet.owner)
        assertEquals(4, snippet.currentVersion.versionNumber)
        assertEquals("new content", snippet.currentVersion.originalContent)
        assertEquals(now, snippet.currentVersion.createdAt)
    }

    @Test
    fun `execute fails when the snippet does not exist`() {
        val id = UUID.randomUUID()
        val failure = Result.Failure(SnippetNotFound(id))
        Mockito.`when`(snippetQueryRepository.findMetadataById(id)).thenReturn(failure)

        assertEquals(failure, useCase.execute(UpdateSnippetContentCommand(id, "test-user", "x")))
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute deletes the stored blob when persisting fails`() {
        val id = UUID.randomUUID()
        val failure = Result.Failure(PersistenceUnavailable("db down"))
        Mockito
            .`when`(snippetQueryRepository.findMetadataById(id))
            .thenReturn(Result.Success(metadata(id)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito.`when`(snippetRepository.update(anySnippet(), Mockito.anyString())).thenReturn(failure)
        Mockito
            .`when`(contentStorage.delete(Mockito.anyString()))
            .thenReturn(Result.Success(Unit))

        assertEquals(failure, useCase.execute(UpdateSnippetContentCommand(id, "test-user", "x")))
        Mockito.verify(contentStorage).delete(Mockito.anyString())
    }

    @Test
    fun `execute rejects content that is too long without touching dependencies`() {
        val content = "a".repeat(SnippetRules.MAX_CONTENT_LENGTH + 1)

        val result = useCase.execute(UpdateSnippetContentCommand(UUID.randomUUID(), "test-user", content))

        assertTrue(result is Result.Failure && result.error is InvalidInput)
        Mockito.verifyNoInteractions(snippetQueryRepository, snippetRepository, contentStorage)
    }

    private fun metadata(id: UUID) = SnippetMetadata(id, "Old name", "Old description", "Kotlin", "2.2", "test-user", 3)

    private fun anySnippet(): Snippet =
        Mockito.any(Snippet::class.java)
            ?: Snippet.create(
                UUID.randomUUID(),
                "",
                "",
                "",
                "",
                "",
                "",
                UUID.randomUUID(),
                now,
            )
}
