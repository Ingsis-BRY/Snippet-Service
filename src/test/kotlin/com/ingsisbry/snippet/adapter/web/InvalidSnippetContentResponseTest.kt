package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.ListSnippetsQueryFactory
import com.ingsisbry.snippet.adapter.web.mapper.SnippetDownloadMapper
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.create.CreateSnippet
import com.ingsisbry.snippet.application.command.create.CreateSnippetCommand
import com.ingsisbry.snippet.application.command.delete.DeleteSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippet
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContent
import com.ingsisbry.snippet.application.command.update.UpdateSnippetContentCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.UnsupportedLanguage
import com.ingsisbry.snippet.application.query.download.DownloadSnippet
import com.ingsisbry.snippet.application.query.get.GetSnippet
import com.ingsisbry.snippet.application.query.list.ListSnippets
import com.ingsisbry.snippet.support.SAMPLE_INVALID_CONTENT
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID

/** The JSON a client gets when the engine rejects the code (US#1-#4): rule, message and position, top level. */
@WebMvcTest(
    controllers = [SnippetController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(SecurityConfiguration::class, WebConfiguration::class, SnippetErrorMapper::class)
class InvalidSnippetContentResponseTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createSnippet: CreateSnippet

    @MockitoBean
    private lateinit var getSnippet: GetSnippet

    @MockitoBean
    private lateinit var updateSnippet: UpdateSnippet

    @MockitoBean
    private lateinit var updateSnippetContent: UpdateSnippetContent

    @MockitoBean
    private lateinit var deleteSnippet: DeleteSnippet

    @MockitoBean
    private lateinit var listSnippets: ListSnippets

    @MockitoBean
    private lateinit var queryFactory: ListSnippetsQueryFactory

    @MockitoBean
    private lateinit var downloadSnippet: DownloadSnippet

    @MockitoBean
    private lateinit var snippetDownloadMapper: SnippetDownloadMapper

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val writer =
        jwt()
            .jwt { it.subject(USER) }
            .authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

    @Test
    fun `create answers 400 with the broken rule, its message and its span at the top level`() {
        // Spring 7 leaves out the default "type" (about:blank), which RFC 9457 allows
        Mockito
            .`when`(createSnippet.execute(command()))
            .thenReturn(Result.Failure(SAMPLE_INVALID_CONTENT))

        mockMvc
            .post("/snippets") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = body("1.1")
            }.andExpect {
                status { isBadRequest() }
                content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
                content {
                    json(
                        """
                        {
                          "title": "InvalidSnippetContent",
                          "status": 400,
                          "detail": "Snippet breaks the rule 'UnexpectedToken' at line 1, column 17: Expected an expression",
                          "instance": "/snippets",
                          "rule": "UnexpectedToken",
                          "message": "Expected an expression",
                          "span": { "start": { "line": 1, "column": 17 }, "end": { "line": 1, "column": 17 } }
                        }
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `a content-only update reports a missing span as null`() {
        val snippetId = UUID.randomUUID()
        Mockito
            .`when`(updateSnippetContent.execute(UpdateSnippetContentCommand(snippetId, USER, "let a = ((((1))));")))
            .thenReturn(Result.Failure(InvalidSnippetContent("NestingTooDeep", "Too deep", null)))

        mockMvc
            .patch("/snippets/$snippetId") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"let a = ((((1))));"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("InvalidSnippetContent") }
                jsonPath("$.detail") { value("Snippet breaks the rule 'NestingTooDeep': Too deep") }
                jsonPath("$.rule") { value("NestingTooDeep") }
                jsonPath("$.span") { value(null) }
                jsonPath("$.properties") { doesNotExist() }
            }
    }

    @Test
    fun `an unsupported version answers 400 UnsupportedLanguage`() {
        Mockito
            .`when`(createSnippet.execute(command(version = "9.9")))
            .thenReturn(Result.Failure(UnsupportedLanguage("printscript", "9.9")))

        mockMvc
            .post("/snippets") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = body("9.9")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("UnsupportedLanguage") }
            }
    }

    private fun command(version: String = "1.1") =
        CreateSnippetCommand(
            name = "bad",
            description = "",
            language = "printscript",
            languageVersion = version,
            owner = USER,
            content = CODE,
        )

    private fun body(version: String) =
        """{"name":"bad","description":"","language":"printscript","languageVersion":"$version","content":"$CODE"}"""

    private companion object {
        const val USER = "auth0|franco"
        const val CODE = "let a: number = ;"
    }
}
