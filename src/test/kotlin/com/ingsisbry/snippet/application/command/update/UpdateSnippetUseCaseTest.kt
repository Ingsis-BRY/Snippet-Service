package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
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

    @BeforeEach
    fun setUp() {
        useCase =
            UpdateSnippetUseCase(
                snippetQueryRepository = snippetQueryRepository,
                snippetRepository = snippetRepository,
                contentStorage = contentStorage,
            )
    }

    @Test
    fun `execute should create a new version and update snippet`() {
        val snippetId = UUID.randomUUID()
        val oldVersionId = UUID.randomUUID()
        val oldCreatedAt = Instant.parse("2026-10-26T12:00:00Z")

        val existingSnippet =
            Snippet.create(
                id = snippetId,
                name = "Old name",
                description = "Old description",
                language = "Kotlin",
                languageVersion = "2.2",
                owner = "test-user",
                content = "println(\"Old\")",
                versionId = oldVersionId,
                createdAt = oldCreatedAt,
            )

        val request =
            UpdateSnippetRequest(
                name = "Updated name",
                description = "Updated description",
                language = "Java",
                languageVersion = "21",
                content = "System.out.println(\"Updated\");",
            )

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(existingSnippet)

        Mockito
            .`when`(
                snippetRepository.update(
                    anySnippet(),
                    Mockito.anyString(),
                ),
            ).thenAnswer { invocation ->
                invocation.getArgument<Snippet>(0)
            }

        val result = useCase.execute(snippetId, request)

        assertNotNull(result)
        result!!

        assertEquals(snippetId, result.id)
        assertEquals(request.name, result.name)
        assertEquals(request.description, result.description)
        assertEquals(request.language, result.language)
        assertEquals(request.languageVersion, result.languageVersion)

        assertEquals(2, result.currentVersion.versionNumber)
        assertEquals(snippetId, result.currentVersion.snippetId)
        assertEquals(request.content, result.currentVersion.originalContent)
        assertNull(result.currentVersion.formattedContent)

        // The update must create a new version rather than reuse the old one.
        assertEquals(false, result.currentVersion.id == oldVersionId)

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(request.content),
        )

        assertEquals(
            "snippets/$snippetId/versions/${result.currentVersion.id}/original",
            keyCaptor.value,
        )

        Mockito.verify(snippetRepository).update(result, keyCaptor.value)
    }

    @Test
    fun `execute should return null when snippet does not exist`() {
        val snippetId = UUID.randomUUID()

        val request =
            UpdateSnippetRequest(
                name = "Updated name",
                description = "Updated description",
                language = "Java",
                languageVersion = "21",
                content = "System.out.println(\"Updated\");",
            )

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(null)

        val result = useCase.execute(snippetId, request)

        assertNull(result)

        Mockito.verify(snippetQueryRepository).findById(snippetId)
        Mockito.verifyNoInteractions(snippetRepository, contentStorage)
    }

    @Test
    fun `execute should delete stored content when repository update fails`() {
        val snippetId = UUID.randomUUID()
        val existingSnippet =
            Snippet.create(
                id = snippetId,
                name = "Original name",
                description = "Original description",
                language = "Kotlin",
                languageVersion = "2.2",
                owner = "test-user",
                content = "println(\"Original\")",
                versionId = UUID.randomUUID(),
                createdAt = Instant.parse("2026-10-26T12:00:00Z"),
            )

        val request =
            UpdateSnippetRequest(
                name = "Updated name",
                description = "Updated description",
                language = "Java",
                languageVersion = "21",
                content = "System.out.println(\"Updated\");",
            )

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(existingSnippet)

        Mockito
            .`when`(
                snippetRepository.update(
                    anySnippet(),
                    Mockito.anyString(),
                ),
            ).thenThrow(
                DataIntegrityViolationException("Database constraint violation"),
            )
        assertThrows(DataIntegrityViolationException::class.java) {
            useCase.execute(snippetId, request)
        }

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(request.content),
        )

        Mockito.verify(contentStorage).delete(keyCaptor.value)
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
                createdAt = Instant.parse("2026-10-26T12:00:00Z"),
            )

    private fun eqString(value: String): String = Mockito.eq(value) ?: value

    private fun captureString(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""
}
