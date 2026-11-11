package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.response.CreateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetCommand
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContent
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContentCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.util.UUID

class UploadSnippetEndpointTest {
    private val createSnippet = Mockito.mock(CreateSnippet::class.java)
    private val updateSnippet = Mockito.mock(UpdateSnippet::class.java)
    private val updateSnippetContent = Mockito.mock(UpdateSnippetContent::class.java)

    private lateinit var controller: SnippetController

    private val snippetId = UUID.randomUUID()
    private val file = MockMultipartFile("file", "example.ps", "text/plain", "println(1)".toByteArray())

    @BeforeEach
    fun setUp() {
        controller =
            SnippetController(
                createSnippet = createSnippet,
                getSnippet = Mockito.mock(GetSnippet::class.java),
                updateSnippet = updateSnippet,
                updateSnippetContent = updateSnippetContent,
                deleteSnippet = Mockito.mock(DeleteSnippet::class.java),
                listSnippets = Mockito.mock(ListSnippets::class.java),
                queryFactory = Mockito.mock(ListSnippetsQueryFactory::class.java),
                downloadSnippet = Mockito.mock(DownloadSnippet::class.java),
                snippetDownloadMapper = Mockito.mock(SnippetDownloadMapper::class.java),
                errorMapper = SnippetErrorMapper(),
            )
    }

    private fun snippet() =
        Snippet.create(
            id = snippetId,
            name = "Example",
            description = "Example snippet",
            language = "PrintScript",
            languageVersion = "1.0",
            owner = "Anonymous",
            content = "println(1)",
            versionId = UUID.randomUUID(),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

    @Test
    fun `create from file returns 201 with the file content as the snippet content`() {
        val command =
            CreateSnippetCommand("Example", "Example snippet", "PrintScript", "1.0", "Anonymous", "println(1)")
        Mockito.`when`(createSnippet.execute(command)).thenReturn(Result.Success(snippet()))

        val response = controller.createFromFile("Example", "Example snippet", "PrintScript", "1.0", file)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(CreateSnippetResponse(id = snippetId), response.body)
    }

    @Test
    fun `create from an empty file returns 400 without creating anything`() {
        val empty = MockMultipartFile("file", "empty.ps", "text/plain", ByteArray(0))

        val response = controller.createFromFile("Example", "Example snippet", "PrintScript", "1.0", empty)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        Mockito.verifyNoInteractions(createSnippet)
    }

    @Test
    fun `update from file returns 200 and replaces all fields`() {
        val command =
            UpdateSnippetCommand(snippetId, "Example", "Example snippet", "PrintScript", "1.0", "println(1)")
        Mockito.`when`(updateSnippet.execute(command)).thenReturn(Result.Success(snippet()))

        val response = controller.updateFromFile(snippetId, "Example", "Example snippet", "PrintScript", "1.0", file)

        assertEquals(HttpStatus.OK, response.statusCode)
        Mockito.verify(updateSnippet).execute(command)
    }

    @Test
    fun `update from file maps a missing snippet to 404`() {
        val command =
            UpdateSnippetCommand(snippetId, "Example", "Example snippet", "PrintScript", "1.0", "println(1)")
        Mockito.`when`(updateSnippet.execute(command)).thenReturn(Result.Failure(SnippetNotFound(snippetId)))

        val response = controller.updateFromFile(snippetId, "Example", "Example snippet", "PrintScript", "1.0", file)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `patch from file returns 200 and only sends the content`() {
        val command = UpdateSnippetContentCommand(snippetId, "println(1)")
        Mockito.`when`(updateSnippetContent.execute(command)).thenReturn(Result.Success(snippet()))

        val response = controller.updateContentFromFile(snippetId, file)

        assertEquals(HttpStatus.OK, response.statusCode)
        Mockito.verify(updateSnippetContent).execute(command)
    }

    @Test
    fun `patch from file maps a missing snippet to 404`() {
        val command = UpdateSnippetContentCommand(snippetId, "println(1)")
        Mockito
            .`when`(updateSnippetContent.execute(command))
            .thenReturn(Result.Failure(SnippetNotFound(snippetId)))

        val response = controller.updateContentFromFile(snippetId, file)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
