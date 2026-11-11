package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UpdateSnippetUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    @Mock
    private lateinit var snippetRepository: SnippetRepository

    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    private lateinit var useCase: UpdateSnippetUseCase

    private var languageCheck: Result<SnippetError, Unit> = Result.Success(Unit)
    private var contentValidation: Result<SnippetError, Unit> = Result.Success(Unit)

    @BeforeEach
    fun setUp() {
        useCase =
            UpdateSnippetUseCase(
                snippetQueryRepository = snippetQueryRepository,
                snippetRepository = snippetRepository,
                contentStorage = contentStorage,
                supportedLanguages = SupportedLanguages { _, _ -> languageCheck },
                contentValidator = SnippetContentValidator { _, _, _ -> contentValidation },
                clock = Clock.fixed(NOW, ZoneOffset.UTC),
            )
    }

    @Test
    fun `execute should reject an unsupported language without touching storage or database`() {
        // given
        languageCheck = Result.Failure(UnsupportedLanguage("Python", "3"))

        // when
        val result = useCase.execute(updateCommand(UUID.randomUUID()))

        // then
        assertEquals(Result.Failure(UnsupportedLanguage("Python", "3")), result)
        Mockito.verifyNoInteractions(snippetQueryRepository, snippetRepository, contentStorage)
    }

    @Test
    fun `execute should reject content that breaks a language rule without touching storage or database`() {
        // given
        contentValidation = Result.Failure(InvalidSnippetContent("no-unused-vars", 3, 7))

        // when
        val result = useCase.execute(updateCommand(UUID.randomUUID()))

        // then
        assertEquals(Result.Failure(InvalidSnippetContent("no-unused-vars", 3, 7)), result)
        Mockito.verifyNoInteractions(snippetQueryRepository, snippetRepository, contentStorage)
    }

    @Test
    fun `execute should create a new version and update snippet`() {
        val snippetId = UUID.randomUUID()
        val command = updateCommand(snippetId)

        Mockito
            .`when`(snippetQueryRepository.findMetadataById(snippetId))
            .thenReturn(Result.Success(metadata(snippetId, versionNumber = 1)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.update(anySnippet(), Mockito.anyString()))
            .thenAnswer { invocation ->
                Result.Success(invocation.getArgument<Snippet>(0))
            }

        val result = useCase.execute(command)

        assertTrue(result is Result.Success)
        val snippet = (result as Result.Success).value

        assertEquals(snippetId, snippet.id)
        assertEquals(command.name, snippet.name)
        assertEquals(command.description, snippet.description)
        assertEquals(command.language, snippet.language)
        assertEquals(command.languageVersion, snippet.languageVersion)
        assertEquals("test-user", snippet.owner)

        assertEquals(2, snippet.currentVersion.versionNumber)
        assertEquals(NOW, snippet.currentVersion.createdAt)
        assertEquals(snippetId, snippet.currentVersion.snippetId)
        assertEquals(command.content, snippet.currentVersion.originalContent)
        assertNull(snippet.currentVersion.formattedContent)

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(command.content),
        )

        assertEquals(
            "snippets/$snippetId/versions/${snippet.currentVersion.id}/original",
            keyCaptor.value,
        )

        Mockito.verify(snippetRepository).update(snippet, keyCaptor.value)

        Mockito.verify(snippetQueryRepository, Mockito.never()).findById(snippetId)
    }

    @Test
    fun `execute should fail with not found when snippet does not exist`() {
        val snippetId = UUID.randomUUID()
        val failure = Result.Failure(SnippetNotFound(snippetId))

        Mockito
            .`when`(snippetQueryRepository.findMetadataById(snippetId))
            .thenReturn(failure)

        val result = useCase.execute(updateCommand(snippetId))

        assertEquals(failure, result)

        Mockito.verify(snippetQueryRepository).findMetadataById(snippetId)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute should delete stored content when repository update fails for any reason`() {
        val snippetId = UUID.randomUUID()
        val command = updateCommand(snippetId)
        val failure = Result.Failure(PersistenceUnavailable("db down"))

        Mockito
            .`when`(snippetQueryRepository.findMetadataById(snippetId))
            .thenReturn(Result.Success(metadata(snippetId, versionNumber = 1)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.update(anySnippet(), Mockito.anyString()))
            .thenReturn(failure)
        Mockito
            .`when`(contentStorage.delete(Mockito.anyString()))
            .thenReturn(Result.Success(Unit))

        val result = useCase.execute(command)

        assertEquals(failure, result)

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(command.content),
        )
        Mockito.verify(contentStorage).delete(keyCaptor.value)
    }

    @Test
    fun `execute should not touch the database when storing content fails`() {
        val snippetId = UUID.randomUUID()
        val failure = Result.Failure(ContentStoreUnavailable("key", "down"))

        Mockito
            .`when`(snippetQueryRepository.findMetadataById(snippetId))
            .thenReturn(Result.Success(metadata(snippetId, versionNumber = 1)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(failure)

        val result = useCase.execute(updateCommand(snippetId))

        assertEquals(failure, result)

        Mockito.verifyNoInteractions(snippetRepository)
        Mockito.verify(contentStorage, Mockito.never()).delete(Mockito.anyString())
    }

    @Test
    fun `execute should fail with conflict and delete content when a concurrent update took the version`() {
        val snippetId = UUID.randomUUID()
        val failure = Result.Failure(SnippetConflict(snippetId))

        Mockito
            .`when`(snippetQueryRepository.findMetadataById(snippetId))
            .thenReturn(Result.Success(metadata(snippetId, versionNumber = 1)))
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.update(anySnippet(), Mockito.anyString()))
            .thenReturn(failure)
        Mockito
            .`when`(contentStorage.delete(Mockito.anyString()))
            .thenReturn(Result.Success(Unit))

        assertEquals(failure, useCase.execute(updateCommand(snippetId)))

        Mockito.verify(contentStorage).delete(Mockito.anyString())
    }

    @Test
    fun `execute should reject invalid input without touching any dependency`() {
        val result = useCase.execute(updateCommand(UUID.randomUUID()).copy(name = " "))

        assertEquals(Result.Failure(InvalidInput("name must not be blank")), result)
        Mockito.verifyNoInteractions(snippetQueryRepository, snippetRepository, contentStorage)
    }

    private fun metadata(
        snippetId: UUID,
        versionNumber: Int,
    ) = SnippetMetadata(
        id = snippetId,
        name = "Old name",
        description = "Old description",
        language = "Kotlin",
        languageVersion = "2.2",
        owner = "test-user",
        currentVersionNumber = versionNumber,
    )

    private fun updateCommand(snippetId: UUID) =
        UpdateSnippetCommand(
            id = snippetId,
            name = "Updated name",
            description = "Updated description",
            language = "Java",
            languageVersion = "21",
            content = "System.out.println(\"Updated\");",
        )

    private fun anySnippet(): Snippet =
        Mockito.any(Snippet::class.java)
            ?: Snippet.create(
                id = UUID.randomUUID(),
                name = "",
                description = "",
                language = "",
                languageVersion = "",
                owner = "",
                content = "",
                versionId = UUID.randomUUID(),
                createdAt = Instant.parse("2026-10-26T12:00:00Z"),
            )

    private fun eqString(value: String): String = Mockito.eq(value) ?: value

    private fun captureString(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-11T10:00:00Z")
    }
}
