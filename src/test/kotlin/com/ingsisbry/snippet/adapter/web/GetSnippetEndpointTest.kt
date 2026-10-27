package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.response.GetSnippetResponse
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GetSnippetEndpointTest {
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
    fun `get returns the requested snippet`() {
        val snippet = createTestSnippet()

        Mockito
            .`when`(
                getSnippet.execute(GetSnippetQuery(snippet.id)),
            ).thenReturn(snippet)

        val response = controller.get(snippet.id)

        assertEquals(GetSnippetResponse.from(snippet), response)

        Mockito.verify(getSnippet).execute(GetSnippetQuery(snippet.id))
    }
}
