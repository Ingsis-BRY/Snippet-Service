package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.rules.RuleUpdate
import com.ingsisbry.snippet.application.command.rules.UpdateRules
import com.ingsisbry.snippet.application.command.rules.UpdateRulesCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.application.query.rules.GetRules
import com.ingsisbry.snippet.application.query.rules.RuleView
import com.ingsisbry.snippet.domain.rules.RuleKind
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
import org.springframework.test.web.servlet.put

@WebMvcTest(
    controllers = [RuleController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(SecurityConfiguration::class, WebConfiguration::class, SnippetErrorMapper::class)
class RuleControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getRules: GetRules

    @MockitoBean
    private lateinit var updateRules: UpdateRules

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val reader = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    private val writer = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

    @Test
    fun `GET format rules answers the UI's Rule shape`() {
        Mockito.`when`(getRules.execute(USER, RuleKind.FORMAT)).thenReturn(Result.Success(VIEWS))

        mockMvc
            .get("/rules/format") { with(reader) }
            .andExpect {
                status { isOk() }
                content {
                    json(
                        """
                        [
                          { "id": "indent-inside-block", "name": "indent-inside-block",
                            "description": "Spaces of indentation inside an if block.", "type": "integer",
                            "isActive": true, "value": 4, "default": null, "options": null, "min": 0, "max": 16,
                            "since": "1.1" },
                          { "id": "space-around-colon", "name": "space-around-colon", "description": "Spaces.",
                            "type": "boolean", "isActive": false, "value": null, "default": false, "options": null,
                            "min": null, "max": null, "since": "1.0" },
                          { "id": "identifier_format", "name": "identifier_format", "description": "Case.",
                            "type": "enum", "isActive": false, "value": "camel case", "default": "camel case",
                            "options": ["camel case", "snake case"], "min": null, "max": null, "since": "1.0" }
                        ]
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `GET lint rules asks for the lint kind`() {
        Mockito.`when`(getRules.execute(USER, RuleKind.LINT)).thenReturn(Result.Success(emptyList()))

        mockMvc.get("/rules/lint") { with(reader) }.andExpect { status { isOk() } }

        Mockito.verify(getRules).execute(USER, RuleKind.LINT)
    }

    @Test
    fun `any other kind is 404`() {
        mockMvc.get("/rules/whatever") { with(reader) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PUT passes id, isActive and value and ignores the other fields`() {
        val command =
            UpdateRulesCommand(
                USER,
                RuleKind.FORMAT,
                listOf(RuleUpdate("indent-inside-block", true, 4), RuleUpdate("space-around-colon", false, null)),
            )
        Mockito.`when`(updateRules.execute(command)).thenReturn(Result.Success(VIEWS))

        mockMvc
            .put("/rules/format") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """[{"id":"indent-inside-block","name":"x","isActive":true,"value":4,"type":"integer"},""" +
                    """{"id":"space-around-colon","isActive":false}]"""
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].isActive") { value(true) }
                jsonPath("$.length()") { value(3) }
            }
    }

    @Test
    fun `an invalid rule is 400 with the reason`() {
        val command = UpdateRulesCommand(USER, RuleKind.LINT, listOf(RuleUpdate("nope", true, null)))
        Mockito.`when`(updateRules.execute(command)).thenReturn(Result.Failure(InvalidInput("Unknown rule 'nope'")))

        mockMvc
            .put("/rules/lint") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """[{"id":"nope","isActive":true}]"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("Unknown rule 'nope'") }
            }
    }

    @Test
    fun `PUT needs the write scope`() {
        mockMvc
            .put("/rules/lint") {
                with(reader)
                contentType = MediaType.APPLICATION_JSON
                content = "[]"
            }.andExpect { status { isForbidden() } }
        Mockito.verifyNoInteractions(updateRules)
    }

    private companion object {
        const val USER = "auth0|franco"
        val VIEWS =
            listOf(
                RuleView(
                    RuleDefinition(
                        "indent-inside-block",
                        RuleType.INTEGER,
                        null,
                        "1.1",
                        "Spaces of indentation inside an if block.",
                        null,
                        0,
                        16,
                    ),
                    active = true,
                    value = 4,
                ),
                RuleView(
                    RuleDefinition("space-around-colon", RuleType.BOOLEAN, false, "1.0", "Spaces.", null, null, null),
                    active = false,
                    value = null,
                ),
                RuleView(
                    RuleDefinition(
                        "identifier_format",
                        RuleType.ENUM,
                        "camel case",
                        "1.0",
                        "Case.",
                        listOf("camel case", "snake case"),
                        null,
                        null,
                    ),
                    active = false,
                    value = "camel case",
                ),
            )
    }
}
