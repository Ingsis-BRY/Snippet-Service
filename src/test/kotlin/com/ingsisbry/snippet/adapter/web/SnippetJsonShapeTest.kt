package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.ProcessingStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContent
import com.ingsisbry.snippet.application.common.pagination.PageResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippetDetails
import com.ingsisbry.snippet.application.query.get.GetSnippetQuery
import com.ingsisbry.snippet.application.query.get.SnippetDetails
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetListItem
import com.ingsisbry.snippet.domain.Snippet
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

/** The JSON of the snippet endpoints the UI reads statuses and owner names from. */
@WebMvcTest(
    controllers = [SnippetController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(
    SecurityConfiguration::class,
    WebConfiguration::class,
    SnippetErrorMapper::class,
    ListSnippetsQueryFactory::class,
)
class SnippetJsonShapeTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createSnippet: CreateSnippet

    @MockitoBean
    private lateinit var getSnippetDetails: GetSnippetDetails

    @MockitoBean
    private lateinit var updateSnippet: UpdateSnippet

    @MockitoBean
    private lateinit var updateSnippetContent: UpdateSnippetContent

    @MockitoBean
    private lateinit var deleteSnippet: DeleteSnippet

    @MockitoBean
    private lateinit var listSnippets: ListSnippets

    @MockitoBean
    private lateinit var downloadSnippet: DownloadSnippet

    @MockitoBean
    private lateinit var snippetDownloadMapper: SnippetDownloadMapper

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val reader = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    private val writer = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

    private val snippet =
        Snippet.create(
            SNIPPET_ID,
            "s",
            "d",
            "printscript",
            "1.1",
            USER,
            "let a: number = 1;",
            VERSION_ID,
            Instant.parse("2026-11-17T10:00:00Z"),
        )

    @Test
    fun `list items carry the owner's name, compliance and processing`() {
        val query = ListSnippetsQuery(user = USER, filters = mapOf("valid" to "false"))
        val item =
            SnippetListItem(
                SNIPPET_ID,
                "s",
                "d",
                "printscript",
                "1.1",
                USER,
                "Franco",
                LintStatus.NOT_COMPLIANT,
                ProcessingStatus.FINISHED,
            )
        Mockito.`when`(listSnippets.execute(query)).thenReturn(Result.Success(PageResult(listOf(item), 0, 20, 1, 1)))

        mockMvc
            .get("/snippets") {
                with(reader)
                param("valid", "false")
            }.andExpect {
                status { isOk() }
                content {
                    json(
                        """
                        { "content": [ { "id": "$SNIPPET_ID", "name": "s", "description": "d", "language": "printscript",
                                         "languageVersion": "1.1", "owner": "$USER", "ownerName": "Franco",
                                         "valid": false, "compliance": "not-compliant", "processing": "finished" } ],
                          "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `create answers the whole snippet, content included`() {
        val command = CreateSnippetCommand("s", "d", "printscript", "1.1", USER, "let a: number = 1;")
        Mockito.`when`(createSnippet.execute(command)).thenReturn(Result.Success(snippet))

        mockMvc
            .post("/snippets") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"name":"s","description":"d","language":"printscript","languageVersion":"1.1",""" +
                    """"content":"let a: number = 1;"}"""
            }.andExpect {
                status { isCreated() }
                content {
                    json(
                        """
                        { "id": "$SNIPPET_ID", "name": "s", "description": "d", "language": "printscript",
                          "languageVersion": "1.1", "owner": "$USER", "versionNumber": 1, "content": "let a: number = 1;" }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `the detail has the analysis, the owner's name and whether the caller owns it`() {
        val analysis =
            SnippetAnalysis(
                ValidationStatus.VALID,
                null,
                LintStatus.NOT_COMPLIANT,
                listOf(CodeIssue("IdentifierFormat", "Use camel case", SPAN)),
                FormatStatus.FINISHED,
            )
        Mockito
            .`when`(getSnippetDetails.execute(GetSnippetQuery(SNIPPET_ID, USER)))
            .thenReturn(Result.Success(SnippetDetails(snippet, "Franco", isOwner = true, analysis = analysis)))

        mockMvc
            .get("/snippets/$SNIPPET_ID") { with(reader) }
            .andExpect {
                status { isOk() }
                content {
                    json(
                        """
                        { "id": "$SNIPPET_ID", "name": "s", "description": "d", "language": "printscript",
                          "languageVersion": "1.1", "owner": "$USER", "ownerName": "Franco", "isOwner": true,
                          "currentVersion": { "id": "$VERSION_ID", "snippetId": "$SNIPPET_ID", "versionNumber": 1,
                                              "originalContent": "let a: number = 1;", "formattedContent": null,
                                              "createdAt": "2026-11-17T10:00:00Z" },
                          "compliance": "not-compliant",
                          "lintIssues": [ { "rule": "IdentifierFormat", "message": "Use camel case",
                                            "span": { "start": { "line": 1, "column": 5 },
                                                      "end": { "line": 1, "column": 7 } } } ],
                          "validationStatus": "valid", "validationError": null, "formatStatus": "finished",
                          "processing": "finished" }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    private companion object {
        const val USER = "auth0|franco"
        val SNIPPET_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val VERSION_ID: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val SPAN = SourceSpan(SourcePosition(1, 5), SourcePosition(1, 7))
    }
}
