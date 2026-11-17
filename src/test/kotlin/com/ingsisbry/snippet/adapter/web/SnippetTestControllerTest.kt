package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.SecurityConfiguration
import com.ingsisbry.snippet.adapter.security.WebConfiguration
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.command.tests.CreateSnippetTest
import com.ingsisbry.snippet.application.command.tests.CreateSnippetTestCommand
import com.ingsisbry.snippet.application.command.tests.DeleteSnippetTest
import com.ingsisbry.snippet.application.command.tests.TestDefinition
import com.ingsisbry.snippet.application.command.tests.UpdateSnippetTest
import com.ingsisbry.snippet.application.command.tests.UpdateSnippetTestCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.query.tests.ListSnippetTests
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.domain.test.SnippetTest
import com.ingsisbry.snippet.support.SAMPLE_SPAN
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(
    controllers = [SnippetTestController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(SecurityConfiguration::class, WebConfiguration::class, SnippetErrorMapper::class)
class SnippetTestControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var listSnippetTests: ListSnippetTests

    @MockitoBean
    private lateinit var createSnippetTest: CreateSnippetTest

    @MockitoBean
    private lateinit var updateSnippetTest: UpdateSnippetTest

    @MockitoBean
    private lateinit var deleteSnippetTest: DeleteSnippetTest

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val reader = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    private val writer = jwt().jwt { it.subject(USER) }.authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

    private val test = SnippetTest(TEST_ID, SNIPPET_ID, "adds", listOf("1", "2"), listOf("3"), Instant.EPOCH)

    @Test
    fun `GET lists the tests in the UI's TestCase shape`() {
        val failed =
            SnippetTestView(
                test,
                TestStatus.ERROR,
                listOf("1"),
                CodeIssue("DivisionByZero", "Zero", SAMPLE_SPAN),
                Instant.parse("2026-11-17T10:00:00Z"),
            )
        Mockito.`when`(listSnippetTests.execute(SNIPPET_ID, USER)).thenReturn(Result.Success(listOf(failed)))

        mockMvc
            .get("/snippets/$SNIPPET_ID/tests") { with(reader) }
            .andExpect {
                status { isOk() }
                content {
                    json(
                        """
                        [ { "id": "$TEST_ID", "snippetId": "$SNIPPET_ID", "name": "adds", "input": ["1", "2"],
                            "output": ["3"], "status": "error", "actualOutput": ["1"],
                            "error": { "rule": "DivisionByZero", "message": "Zero",
                                       "span": { "start": { "line": 1, "column": 17 }, "end": { "line": 1, "column": 17 } } },
                            "lastRunAt": "2026-11-17T10:00:00Z" } ]
                        """.trimIndent(),
                        JsonCompareMode.STRICT,
                    )
                }
            }
    }

    @Test
    fun `POST creates a pending test and answers 201`() {
        val command = CreateSnippetTestCommand(SNIPPET_ID, USER, TestDefinition("adds", listOf("1", "2"), listOf("3")))
        Mockito
            .`when`(createSnippetTest.execute(command))
            .thenReturn(Result.Success(SnippetTestView(test, TestStatus.PENDING, null, null, null)))

        mockMvc
            .post("/snippets/$SNIPPET_ID/tests") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"adds","input":["1","2"],"output":["3"]}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.status") { value("pending") }
                jsonPath("$.actualOutput") { value(null) }
                jsonPath("$.error") { value(null) }
                jsonPath("$.lastRunAt") { value(null) }
            }
    }

    @Test
    fun `input and output may be omitted`() {
        val definition = TestDefinition("prints nothing", emptyList(), emptyList())
        val command = CreateSnippetTestCommand(SNIPPET_ID, USER, definition)
        Mockito
            .`when`(createSnippetTest.execute(command))
            .thenReturn(Result.Success(SnippetTestView(test, TestStatus.PENDING, null, null, null)))

        mockMvc
            .post("/snippets/$SNIPPET_ID/tests") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"prints nothing"}"""
            }.andExpect { status { isCreated() } }
    }

    @Test
    fun `too many tests is a 400`() {
        val command = CreateSnippetTestCommand(SNIPPET_ID, USER, TestDefinition("t", emptyList(), emptyList()))
        Mockito
            .`when`(createSnippetTest.execute(command))
            .thenReturn(Result.Failure(InvalidInput("A snippet can have at most 50 tests")))

        mockMvc
            .post("/snippets/$SNIPPET_ID/tests") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"t"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("A snippet can have at most 50 tests") }
            }
    }

    @Test
    fun `a reader who tries to write gets 403`() {
        val command = CreateSnippetTestCommand(SNIPPET_ID, USER, TestDefinition("t", emptyList(), emptyList()))
        Mockito.`when`(createSnippetTest.execute(command)).thenReturn(Result.Failure(AccessDenied(SNIPPET_ID, USER)))

        mockMvc
            .post("/snippets/$SNIPPET_ID/tests") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"t"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `PUT answers 200, or 404 TestNotFound for a test of another snippet`() {
        val definition = TestDefinition("adds", listOf("1"), listOf("1"))
        Mockito
            .`when`(updateSnippetTest.execute(UpdateSnippetTestCommand(SNIPPET_ID, TEST_ID, USER, definition)))
            .thenReturn(Result.Success(SnippetTestView(test, TestStatus.PENDING, null, null, null)))
        val other = UUID.randomUUID()
        Mockito
            .`when`(updateSnippetTest.execute(UpdateSnippetTestCommand(SNIPPET_ID, other, USER, definition)))
            .thenReturn(Result.Failure(TestNotFound(SNIPPET_ID, other)))

        mockMvc
            .put("/snippets/$SNIPPET_ID/tests/$TEST_ID") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"adds","input":["1"],"output":["1"]}"""
            }.andExpect { status { isOk() } }
        mockMvc
            .put("/snippets/$SNIPPET_ID/tests/$other") {
                with(writer)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"adds","input":["1"],"output":["1"]}"""
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.title") { value("TestNotFound") }
                jsonPath("$.detail") { value("Test $other not found in snippet $SNIPPET_ID") }
            }
    }

    @Test
    fun `DELETE answers 204`() {
        Mockito.`when`(deleteSnippetTest.execute(SNIPPET_ID, TEST_ID, USER)).thenReturn(Result.Success(Unit))

        mockMvc.delete("/snippets/$SNIPPET_ID/tests/$TEST_ID") { with(writer) }.andExpect { status { isNoContent() } }
    }

    private companion object {
        const val USER = "auth0|franco"
        val SNIPPET_ID: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val TEST_ID: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    }
}
