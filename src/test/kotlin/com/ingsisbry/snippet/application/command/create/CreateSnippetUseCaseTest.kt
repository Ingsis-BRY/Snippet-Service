package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(MockitoExtension::class)
class CreateSnippetUseCaseTest {
    @Mock
    private lateinit var snippetRepository: SnippetRepository

    @Mock
    private lateinit var contentStorage: SnippetContentStorage

    private lateinit var useCase: CreateSnippetUseCase

    private val createdAt = Instant.parse("2026-10-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        val clock = Clock.fixed(createdAt, ZoneOffset.UTC)

        useCase =
            CreateSnippetUseCase(
                snippetRepository = snippetRepository,
                contentStorage = contentStorage,
                clock = clock,
            )
    }

    @Test
    fun `execute should store content and save snippet`() {
        val command =
            CreateSnippetCommand(
                name = "Hello World",
                description = "A test snippet",
                language = "Kotlin",
                languageVersion = "2.3",
                owner = "test-user",
                content = "fun main() = println(\"Hello\")",
            )

        Mockito
            .`when`(
                snippetRepository.save(
                    anySnippet(),
                    Mockito.anyString(),
                ),
            ).thenAnswer { invocation ->
                invocation.getArgument<Snippet>(0)
            }

        val result = useCase.execute(command)

        assertEquals(command.name, result.name)
        assertEquals(command.description, result.description)
        assertEquals(command.language, result.language)
        assertEquals(command.languageVersion, result.languageVersion)
        assertEquals(command.owner, result.owner)
        assertEquals(command.content, result.currentVersion.originalContent)
        assertEquals(1, result.currentVersion.versionNumber)
        assertEquals(createdAt, result.currentVersion.createdAt)
        assertNotNull(result.id)
        assertNotNull(result.currentVersion.id)

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(command.content),
        )

        val contentKey = keyCaptor.value

        assertEquals(
            "snippets/${result.id}/versions/${result.currentVersion.id}/original",
            contentKey,
        )

        Mockito.verify(snippetRepository).save(result, contentKey)
    }

    @Test
    fun `execute should delete stored content when repository save fails`() {
        val command =
            CreateSnippetCommand(
                name = "Hello World",
                description = "A test snippet",
                language = "Kotlin",
                languageVersion = "2.3",
                owner = "test-user",
                content = "println(\"Hello\")",
            )

        Mockito
            .`when`(
                snippetRepository.save(
                    anySnippet(),
                    Mockito.anyString(),
                ),
            ).thenThrow(
                DataIntegrityViolationException("Database constraint violation"),
            )

        assertThrows(DataIntegrityViolationException::class.java) {
            useCase.execute(command)
        }

        val keyCaptor = ArgumentCaptor.forClass(String::class.java)

        Mockito.verify(contentStorage).store(
            captureString(keyCaptor),
            eqString(command.content),
        )

        Mockito.verify(contentStorage).delete(keyCaptor.value)
    }

    private fun anySnippet(): Snippet =
        Mockito.any(Snippet::class.java)
            ?: Snippet.create(
                id = java.util.UUID.randomUUID(),
                name = "",
                description = "",
                language = "",
                languageVersion = "",
                owner = "",
                content = "",
                versionId = java.util.UUID.randomUUID(),
                createdAt = createdAt,
            )

    private fun eqString(value: String): String = Mockito.eq(value) ?: value

    private fun captureString(captor: ArgumentCaptor<String>): String = captor.capture() ?: ""
}
