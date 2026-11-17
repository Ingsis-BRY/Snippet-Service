package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.command.format.OwnerFormatter
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidSnippetContent
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfig
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.SAMPLE_SPAN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DownloadSnippetUseCaseTest {
    @Mock
    private lateinit var snippetQueryRepository: SnippetQueryRepository

    private lateinit var useCase: DownloadSnippetUseCase

    private val snippetId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val originalContent = "let a:number=1;"

    private val permissions = FakeSnippetPermissions()

    private val formatCalls = mutableListOf<Pair<String, Map<String, Any>>>()
    private var formatResult: Result<SnippetError, FormatOutcome> =
        Result.Success(FormatOutcome("let a: number = 1;\n", null, emptyList()))
    private val formatter =
        SnippetFormatter { _, _, code, config ->
            formatCalls += code to config
            formatResult
        }
    private val ruleConfig =
        ResolveRuleConfig { userId, kind ->
            if (userId == OWNER && kind == RuleKind.FORMAT) mapOf("space-around-colon" to true) else emptyMap()
        }

    @BeforeEach
    fun setUp() {
        useCase = DownloadSnippetUseCase(snippetQueryRepository, permissions, OwnerFormatter(formatter, ruleConfig))
    }

    @Test
    fun `execute should deny the download without loading the snippet when the user cannot read it`() {
        permissions.allowed = false

        val result = useCase.execute(snippetId, "intruder", SnippetContentVariant.ORIGINAL)

        assertEquals(Result.Failure(AccessDenied(snippetId, "intruder")), result)
        Mockito.verifyNoInteractions(snippetQueryRepository)
    }

    @Test
    fun `execute should return original content when variant is ORIGINAL`() {
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet()))

        val result = useCase.execute(snippetId, "reader", SnippetContentVariant.ORIGINAL)

        assertEquals(Result.Success(originalContent), result)
        assertTrue(formatCalls.isEmpty())
    }

    @Test
    fun `the stored formatted version is served as is`() {
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet(formatted = "stored formatted")))

        val result = useCase.execute(snippetId, "reader", SnippetContentVariant.FORMATTED)

        assertEquals(Result.Success("stored formatted"), result)
        assertTrue(formatCalls.isEmpty())
    }

    @Test
    fun `without a stored formatted version the snippet is formatted now with the owner's rules`() {
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet()))

        val result = useCase.execute(snippetId, "reader", SnippetContentVariant.FORMATTED)

        assertEquals(Result.Success("let a: number = 1;\n"), result)
        assertEquals(listOf(originalContent to mapOf<String, Any>("space-around-colon" to true)), formatCalls)
    }

    @Test
    fun `code the formatter can't parse is invalid content`() {
        val issue = CodeIssue("UnexpectedToken", "Expected ';'", SAMPLE_SPAN)
        formatResult = Result.Success(FormatOutcome(null, issue, emptyList()))
        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(Result.Success(createSnippet()))

        val result = useCase.execute(snippetId, "reader", SnippetContentVariant.FORMATTED)

        assertEquals(Result.Failure(InvalidSnippetContent("UnexpectedToken", "Expected ';'", SAMPLE_SPAN)), result)
    }

    @Test
    fun `execute should propagate the error when the snippet cannot be loaded`() {
        val failure = Result.Failure(SnippetNotFound(snippetId))

        Mockito
            .`when`(snippetQueryRepository.findById(snippetId))
            .thenReturn(failure)

        val result = useCase.execute(snippetId, "reader", SnippetContentVariant.ORIGINAL)

        assertEquals(failure, result)
    }

    private fun createSnippet(formatted: String? = null): Snippet =
        Snippet(
            id = snippetId,
            name = "Hello World",
            description = "Test snippet",
            language = "printscript",
            languageVersion = "1.1",
            owner = OWNER,
            currentVersion =
                SnippetVersion(
                    id = versionId,
                    snippetId = snippetId,
                    versionNumber = 1,
                    originalContent = originalContent,
                    formattedContent = formatted,
                    createdAt = Instant.parse("2026-10-27T12:00:00Z"),
                ),
        )

    private companion object {
        const val OWNER = "auth0|owner"
    }
}
