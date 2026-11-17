package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.format.FormatSnippet
import com.ingsisbry.snippet.application.command.format.FormatSnippetCommand
import com.ingsisbry.snippet.application.command.format.FormattedSnippet
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import com.ingsisbry.snippet.application.query.language.ListLanguages
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@WebMvcTest(
    controllers = [LanguageController::class, SnippetFormatController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(SecurityConfiguration::class, WebConfiguration::class, SnippetErrorMapper::class)
class LanguageAndFormatEndpointTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var listLanguages: ListLanguages

    @MockitoBean
    private lateinit var formatSnippet: FormatSnippet

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val reader = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    private val writer = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

    @Test
    fun `GET languages lists each language with its extensions and versions`() {
        Mockito
            .`when`(listLanguages.execute())
            .thenReturn(
                Result.Success(
                    listOf(LanguageInfo("printscript", listOf("prs", "ps"), listOf("1.0", "1.1"), "1.0", "1.1.3")),
                ),
            )

        mockMvc
            .get("/languages") { with(reader) }
            .andExpect {
                status { isOk() }
                content {
                    json(
                        """
                        [ { "language": "printscript", "extension": "prs", "extensions": ["prs", "ps"],
                            "versions": ["1.0", "1.1"], "defaultVersion": "1.0" } ]
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `GET languages is 503 when the engine is down`() {
        Mockito.`when`(listLanguages.execute()).thenReturn(Result.Failure(LanguageServiceUnavailable("down")))

        mockMvc
            .get("/languages") { with(reader) }
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.title") { value("LanguageServiceUnavailable") }
                jsonPath("$.detail") { value("Language service is unavailable") }
            }
    }

    @Test
    fun `POST format answers the formatted code`() {
        Mockito
            .`when`(formatSnippet.execute(FormatSnippetCommand(SNIPPET_ID, USER, "let a:number=1;")))
            .thenReturn(Result.Success(FormattedSnippet("let a: number = 1;\n", emptyList())))

        mockMvc
            .post("/snippets/$SNIPPET_ID/format") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"let a:number=1;"}"""
            }.andExpect {
                status { isOk() }
                content { json("""{"formatted":"let a: number = 1;\n","ignoredRules":[]}""", JsonCompareMode.STRICT) }
            }
    }

    @Test
    fun `POST format without content formats the stored version`() {
        Mockito
            .`when`(formatSnippet.execute(FormatSnippetCommand(SNIPPET_ID, USER, null)))
            .thenReturn(Result.Success(FormattedSnippet("x\n", emptyList())))

        mockMvc
            .post("/snippets/$SNIPPET_ID/format") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isOk() } }
    }

    @Test
    fun `POST format reports code it can't parse like an invalid snippet`() {
        Mockito
            .`when`(formatSnippet.execute(FormatSnippetCommand(SNIPPET_ID, USER, "let a: number = ;")))
            .thenReturn(Result.Failure(SAMPLE_INVALID_CONTENT))

        mockMvc
            .post("/snippets/$SNIPPET_ID/format") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"content":"let a: number = ;"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.title") { value("InvalidSnippetContent") }
                jsonPath("$.rule") { value("UnexpectedToken") }
                jsonPath("$.span.start.column") { value(17) }
            }
    }

    private companion object {
        const val USER = "auth0|franco"
        val SNIPPET_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    }
}
