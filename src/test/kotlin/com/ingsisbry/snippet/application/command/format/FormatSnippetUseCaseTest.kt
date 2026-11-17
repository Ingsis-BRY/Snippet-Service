package com.ingsisbry.snippet.application.command.format

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfig
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.SAMPLE_SPAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class FormatSnippetUseCaseTest {
    @Mock
    private lateinit var queryRepository: SnippetQueryRepository

    private val permissions = FakeSnippetPermissions()
    private val snippetId = UUID.randomUUID()
    private val calls = mutableListOf<List<Any>>()
    private var outcome: Result<SnippetError, FormatOutcome> =
        Result.Success(FormatOutcome("let a: number = 1;\n", null, listOf("if-brace-same-line")))
    private val formatter =
        SnippetFormatter { language, version, code, config ->
            calls += listOf(language, version, code, config)
            outcome
        }
    private val ruleConfig =
        ResolveRuleConfig { userId, kind ->
            if (userId == OWNER && kind == RuleKind.FORMAT) OWNER_CONFIG else emptyMap()
        }

    private val useCase by lazy {
        FormatSnippetUseCase(queryRepository, permissions, OwnerFormatter(formatter, ruleConfig))
    }

    @Test
    fun `a reader formats sent content with the owner's rules and the snippet's version`() {
        Mockito.`when`(queryRepository.findMetadataById(snippetId)).thenReturn(Result.Success(metadata()))

        val result = useCase.execute(FormatSnippetCommand(snippetId, READER, "let a:number=1;"))

        assertEquals(Result.Success(FormattedSnippet("let a: number = 1;\n", listOf("if-brace-same-line"))), result)
        assertEquals(listOf(listOf("printscript", "1.0", "let a:number=1;", OWNER_CONFIG)), calls)
        assertEquals(SnippetAction.READ, permissions.checks.single().third)
    }

    @Test
    fun `without content the current version is formatted`() {
        val snippet =
            Snippet.create(
                id = snippetId,
                name = "s",
                description = "",
                language = "printscript",
                languageVersion = "1.0",
                owner = OWNER,
                content = "println(1);",
                versionId = UUID.randomUUID(),
                createdAt = Instant.EPOCH,
            )
        Mockito.`when`(queryRepository.findById(snippetId)).thenReturn(Result.Success(snippet))

        useCase.execute(FormatSnippetCommand(snippetId, READER, null))

        assertEquals("println(1);", calls.single()[2])
    }

    @Test
    fun `without READ nothing is loaded or formatted`() {
        permissions.allowed = false

        val result = useCase.execute(FormatSnippetCommand(snippetId, "intruder", "x"))

        assertEquals(Result.Failure(AccessDenied(snippetId, "intruder")), result)
        Mockito.verifyNoInteractions(queryRepository)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `code the formatter can't parse is invalid content with its position`() {
        val issue = CodeIssue("UnexpectedToken", "Expected ';'", SAMPLE_SPAN)
        outcome = Result.Success(FormatOutcome(null, issue, emptyList()))
        Mockito.`when`(queryRepository.findMetadataById(snippetId)).thenReturn(Result.Success(metadata()))

        val result = useCase.execute(FormatSnippetCommand(snippetId, READER, "let a"))

        assertEquals(Result.Failure(InvalidSnippetContent("UnexpectedToken", "Expected ';'", SAMPLE_SPAN)), result)
    }

    @Test
    fun `content longer than a snippet can be is rejected first`() {
        val result = useCase.execute(FormatSnippetCommand(snippetId, READER, "a".repeat(10_001)))

        assertEquals(Result.Failure(InvalidInput("content must be at most 10000 characters")), result)
        assertTrue(permissions.checks.isEmpty())
    }

    private fun metadata() = SnippetMetadata(snippetId, "s", "", "printscript", "1.0", OWNER, 1)

    private companion object {
        const val OWNER = "auth0|owner"
        const val READER = "auth0|reader"
        val OWNER_CONFIG = mapOf<String, Any>("space-around-colon" to true)
    }
}
