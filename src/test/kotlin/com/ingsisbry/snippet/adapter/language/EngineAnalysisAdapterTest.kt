package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.LanguageServiceUnavailable
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import com.ingsisbry.snippet.application.port.out.language.LintOutcome
import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.application.port.out.language.TestOutcome
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.support.SAMPLE_SPAN
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class EngineAnalysisAdapterTest {
    private val shortBuilder = engineClientBuilder(BASE_URL)
    private val shortServer = MockRestServiceServer.bindTo(shortBuilder).build()
    private val longBuilder = engineClientBuilder(BASE_URL)
    private val longServer = MockRestServiceServer.bindTo(longBuilder).build()
    private val adapter =
        EngineAnalysisAdapter(
            shortBuilder.build(),
            longBuilder.build(),
            SnippetLanguagesProperties(listOf(PRINTSCRIPT)),
        )

    @AfterEach
    fun verify() {
        shortServer.verify()
        longServer.verify()
    }

    @Test
    fun `languages come from each engine's versions, with the library version`() {
        shortServer
            .expect(requestTo("$BASE_URL/versions"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andRespond(
                withSuccess(
                    """{"language":"printscript","versions":["1.0","1.1"],"default":"1.0","libraryVersion":"1.1.3"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            Result.Success(listOf(LanguageInfo("printscript", listOf("prs", "ps"), VERSIONS, "1.0", "1.1.3"))),
            adapter.languages(),
        )
    }

    @Test
    fun `an older engine without libraryVersion still lists its versions`() {
        shortServer
            .expect(requestTo("$BASE_URL/versions"))
            .andRespond(json("""{"language":"printscript","versions":["1.0"],"default":"1.0"}"""))

        val languages = (adapter.languages() as Result.Success).value

        assertEquals(null, languages.single().libraryVersion)
    }

    @Test
    fun `an engine that is down makes the languages unavailable`() {
        shortServer
            .expect(ExpectedCount.times(2), requestTo("$BASE_URL/versions"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

        assertTrue((adapter.languages() as Result.Failure).error is LanguageServiceUnavailable)
    }

    @Test
    fun `the rule catalog is every rule of every version`() {
        shortServer
            .expect(requestTo("$BASE_URL/rules/format"))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andRespond(
                withSuccess(
                    """[{"key":"indent-inside-block","type":"integer","default":4,"since":"1.1",""" +
                        """"description":"Indent.","min":0,"max":16},""" +
                        """{"key":"space-around-colon","type":"boolean","default":false,"since":"1.0",""" +
                        """"description":"Colon."},""" +
                        """{"key":"identifier_format","type":"enum","default":"camel case","since":"1.0",""" +
                        """"description":"Case.","options":["camel case","snake case"]}]""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            Result.Success(
                listOf(
                    RuleDefinition("indent-inside-block", RuleType.INTEGER, 4, "1.1", "Indent.", null, 0, 16),
                    RuleDefinition("space-around-colon", RuleType.BOOLEAN, false, "1.0", "Colon.", null, null, null),
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
                ),
            ),
            adapter.rules(RuleKind.FORMAT),
        )
    }

    @Test
    fun `lint sends the config and joins the violations and the parse error`() {
        shortServer
            .expect(requestTo("$BASE_URL/lint"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andExpect(
                content().json(
                    """{"code":"$CODE","version":"1.1","config":{"identifier_format":"snake case","x":true}}""",
                    JsonCompareMode.STRICT,
                ),
            ).andRespond(
                withSuccess(
                    """{"valid":false,"violations":[$ISSUE_JSON],"error":null,"ignoredRules":[]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            Result.Success(LintOutcome(false, listOf(ISSUE))),
            adapter.lint("printscript", "1.1", CODE, mapOf("identifier_format" to "snake case", "x" to true)),
        )
    }

    @Test
    fun `a config the engine refuses is invalid input`() {
        shortServer
            .expect(requestTo("$BASE_URL/lint"))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body("""{"title":"InvalidConfig","status":400,"detail":"$BAD_CONFIG"}"""),
            )

        assertEquals(
            Result.Failure(InvalidInput(BAD_CONFIG)),
            adapter.lint("printscript", "1.1", "", mapOf("identifier_format" to 3)),
        )
    }

    @Test
    fun `format answers the formatted code, or the issue that stopped it`() {
        shortServer
            .expect(requestTo("$BASE_URL/format"))
            .andExpect(content().json("""{"code":"$CODE","version":"1.0","config":{}}""", JsonCompareMode.STRICT))
            .andRespond(
                withSuccess(
                    """{"formatted":"let a: number = 1;\n","error":null,"ignoredRules":["if-brace-same-line"]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        shortServer
            .expect(requestTo("$BASE_URL/format"))
            .andRespond(json("""{"formatted":null,"error":$ISSUE_JSON,"ignoredRules":[]}"""))

        assertEquals(
            Result.Success(FormatOutcome("let a: number = 1;\n", null, listOf("if-brace-same-line"))),
            adapter.format("printscript", "1.0", CODE, emptyMap()),
        )
        assertEquals(
            Result.Success(FormatOutcome(null, ISSUE, emptyList())),
            adapter.format("printscript", "1.0", CODE, emptyMap()),
        )
    }

    @Test
    fun `tests run on the long client, ten per request`() {
        val tests = (1..12).map { TestSpec("t$it", listOf("in$it"), listOf("out$it")) }
        longServer
            .expect(requestTo("$BASE_URL/test"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(LANGUAGE_HEADER, "printscript"))
            .andRespond(withSuccess(results(1..10), MediaType.APPLICATION_JSON))
        longServer
            .expect(requestTo("$BASE_URL/test"))
            .andExpect(
                content().json(
                    """{"code":"$CODE","version":"1.1","tests":[""" +
                        """{"id":"t11","inputs":["in11"],"expectedOutputs":["out11"]},""" +
                        """{"id":"t12","inputs":["in12"],"expectedOutputs":["out12"]}]}""",
                    JsonCompareMode.STRICT,
                ),
            ).andRespond(withSuccess(results(11..12), MediaType.APPLICATION_JSON))

        val outcomes = (adapter.run("printscript", "1.1", CODE, tests) as Result.Success).value

        assertEquals(12, outcomes.size)
        assertEquals(TestOutcome("t1", true, listOf("out1"), null), outcomes.first())
        assertEquals(TestOutcome("t12", false, listOf("x"), ISSUE), outcomes.last())
    }

    @Test
    fun `an engine answer missing a test breaks the contract`() {
        longServer
            .expect(requestTo("$BASE_URL/test"))
            .andRespond(withSuccess("""{"results":[]}""", MediaType.APPLICATION_JSON))

        val result = adapter.run("printscript", "1.1", CODE, listOf(TestSpec("t1", emptyList(), emptyList())))

        assertTrue((result as Result.Failure).error is LanguageServiceUnavailable)
    }

    private fun json(body: String) = withSuccess(body, MediaType.APPLICATION_JSON)

    private fun results(ids: IntRange): String =
        ids.joinToString(",", """{"results":[""", "]}") { id ->
            if (id == 12) {
                """{"id":"t12","passed":false,"actualOutputs":["x"],"expectedOutputs":["out12"],"error":$ISSUE_JSON}"""
            } else {
                """{"id":"t$id","passed":true,"actualOutputs":["out$id"],"expectedOutputs":["out$id"],"error":null}"""
            }
        }

    private companion object {
        const val BASE_URL = "http://engine-proxy"
        const val LANGUAGE_HEADER = "X-Language"
        const val CODE = "let a:number=1;"
        const val BAD_CONFIG = "identifier_format must be camel or snake"
        val VERSIONS = listOf("1.0", "1.1")
        val PRINTSCRIPT = SnippetLanguagesProperties.LanguageProperties("printscript", listOf("prs", "ps"))
        const val ISSUE_JSON =
            """{"rule":"UnexpectedToken","message":"Expected an expression",""" +
                """"span":{"start":{"line":1,"column":17},"end":{"line":1,"column":17}}}"""
        val ISSUE = CodeIssue("UnexpectedToken", "Expected an expression", SAMPLE_SPAN)
    }
}
