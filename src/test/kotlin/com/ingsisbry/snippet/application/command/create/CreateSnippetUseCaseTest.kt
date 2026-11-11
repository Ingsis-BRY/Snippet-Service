package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.ContentStoreUnavailable
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class CreateSnippetUseCaseTest {
    @Mock
    private lateinit var snippetRepository: SnippetRepository

    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    private lateinit var useCase: CreateSnippetUseCase

    private var languageCheck: Result<SnippetError, Unit> = Result.Success(Unit)
    private var contentValidation: Result<SnippetError, Unit> = Result.Success(Unit)

    private val createdAt = Instant.parse("2026-10-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        val clock = Clock.fixed(createdAt, ZoneOffset.UTC)

        useCase =
            CreateSnippetUseCase(
                snippetRepository = snippetRepository,
                contentStorage = contentStorage,
                supportedLanguages = SupportedLanguages { _, _ -> languageCheck },
                contentValidator = SnippetContentValidator { _, _, _ -> contentValidation },
                clock = clock,
            )
    }

    @Test
    fun `execute should reject an unsupported language without touching storage or database`() {
        // given
        languageCheck = Result.Failure(UnsupportedLanguage("Python", "3"))

        // when
        val result = useCase.execute(command)

        // then
        assertEquals(Result.Failure(UnsupportedLanguage("Python", "3")), result)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute should reject content that breaks a language rule without touching storage or database`() {
        // given
        contentValidation = Result.Failure(InvalidSnippetContent("no-unused-vars", 3, 7))

        // when
        val result = useCase.execute(command)

        // then
        assertEquals(Result.Failure(InvalidSnippetContent("no-unused-vars", 3, 7)), result)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute should reject invalid input without touching storage or database`() {
        val result = useCase.execute(command.copy(language = ""))

        assertEquals(Result.Failure(InvalidInput("language must not be blank")), result)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    private val command =
        CreateSnippetCommand(
            name = "Hello World",
            description = "A test snippet",
            language = "Kotlin",
            languageVersion = "2.3",
            owner = "test-user",
            content = "fun main() = println(\"Hello\")",
        )

    @Test
    fun `execute should store content and save snippet`() {
        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))

        Mockito
            .`when`(snippetRepository.save(anySnippet(), Mockito.anyString()))
            .thenAnswer { invocation ->
                Result.Success(invocation.getArgument<Snippet>(0))
            }

        val result = useCase.execute(command)

        assertTrue(result is Result.Success)
        val snippet = (result as Result.Success).value

        assertEquals(command.name, snippet.name)
        assertEquals(command.description, snippet.description)
        assertEquals(command.language, snippet.language)
        assertEquals(command.languageVersion, snippet.languageVersion)
        assertEquals(command.owner, snippet.owner)
        assertEquals(command.content, snippet.currentVersion.originalContent)
        assertEquals(1, snippet.currentVersion.versionNumber)
        assertEquals(createdAt, snippet.currentVersion.createdAt)
        assertNotNull(snippet.id)
        assertNotNull(snippet.currentVersion.id)

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(command.content),
        )

        assertEquals(
            "snippets/${snippet.id}/versions/${snippet.currentVersion.id}/original",
            keyCaptor.value,
        )

        Mockito.verify(snippetRepository).save(snippet, keyCaptor.value)
        Mockito.verify(contentStorage, Mockito.never()).delete(Mockito.anyString())
    }

    @Test
    fun `execute should delete stored content when saving fails for any reason`() {
        val failure = Result.Failure(PersistenceUnavailable("db down"))

        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.save(anySnippet(), Mockito.anyString()))
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
    fun `execute should return the original error when cleanup also fails`() {
        val failure = Result.Failure(SnippetConflict(UUID.randomUUID()))

        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(Result.Success(Unit))
        Mockito
            .`when`(snippetRepository.save(anySnippet(), Mockito.anyString()))
            .thenReturn(failure)
        Mockito
            .`when`(contentStorage.delete(Mockito.anyString()))
            .thenReturn(Result.Failure(ContentStoreUnavailable("key", "down")))

        val result = useCase.execute(command)

        assertEquals(failure, result)
    }

    @Test
    fun `execute should not touch the database when storing content fails`() {
        val failure = Result.Failure(ContentStoreUnavailable("key", "down"))

        Mockito
            .`when`(contentStorage.store(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(failure)

        val result = useCase.execute(command)

        assertEquals(failure, result)

        Mockito.verifyNoInteractions(snippetRepository)
        Mockito.verify(contentStorage, Mockito.never()).delete(Mockito.anyString())
    }

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
                createdAt = createdAt,
            )

    private fun eqString(value: String): String = Mockito.eq(value) ?: value

    private fun captureString(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""
}
