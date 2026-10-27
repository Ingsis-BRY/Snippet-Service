package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.adapter.web.dto.response.UpdateSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UpdateSnippetEndpointTest {
    @Mock
    private lateinit var listSnippets: ListSnippets

    @Mock
    private lateinit var queryFactory: ListSnippetsQueryFactory

    @InjectMocks
    private lateinit var controller: SnippetController

    @Mock
    private lateinit var createSnippet: CreateSnippet

    @Mock
    private lateinit var getSnippet: GetSnippet

    @Mock
    private lateinit var updateSnippet: UpdateSnippet

    @Mock
    private lateinit var deleteSnippet: DeleteSnippet

    @Mock
    private lateinit var downloadSnippet: DownloadSnippet

    @Mock
    private lateinit var snippetDownloadMapper: SnippetDownloadMapper

    @BeforeEach
    fun setUp() {
        controller =
            SnippetController(
                createSnippet = createSnippet,
                getSnippet = getSnippet,
                updateSnippet = updateSnippet,
                deleteSnippet = deleteSnippet,
                listSnippets = listSnippets,
                queryFactory = queryFactory,
                downloadSnippet = downloadSnippet,
                snippetDownloadMapper = snippetDownloadMapper,
            )
    }

    private fun createTestSnippet(): Snippet {
        val snippetId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        return Snippet(
            id = snippetId,
            name = "Example",
            description = "Example snippet",
            language = "PrintScript",
            languageVersion = "1.0",
            owner = "Anonymous",
            currentVersion =
                SnippetVersion.first(
                    id = versionId,
                    snippetId = snippetId,
                    originalContent = "println(1)",
                    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
        )
    }

    @Test
    fun `update returns the updated snippet`() {
        val snippet = createTestSnippet()

        val request =
            UpdateSnippetRequest(
                name = "Updated example",
                description = "Updated description",
                language = "PrintScript",
                languageVersion = "1.0",
                content = "println(2)",
            )

        val updatedSnippet =
            snippet.copy(
                name = request.name,
                description = request.description,
                language = request.language,
                languageVersion = request.languageVersion,
            )

        Mockito
            .`when`(
                updateSnippet.execute(snippet.id, request),
            ).thenReturn(updatedSnippet)

        val response = controller.update(snippet.id, request)

        assertEquals(UpdateSnippetResponse.from(updatedSnippet), response)

        Mockito.verify(updateSnippet).execute(snippet.id, request)
    }

    @Test
    fun `update throws not found when snippet does not exist`() {
        val snippetId = UUID.randomUUID()

        val request =
            UpdateSnippetRequest(
                name = "Example",
                description = "Description",
                language = "PrintScript",
                languageVersion = "1.0",
                content = "println(1)",
            )

        Mockito
            .`when`(
                updateSnippet.execute(snippetId, request),
            ).thenReturn(null)

        val exception =
            assertThrows<ResponseStatusException> {
                controller.update(snippetId, request)
            }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)

        Mockito.verify(updateSnippet).execute(snippetId, request)
    }
}
