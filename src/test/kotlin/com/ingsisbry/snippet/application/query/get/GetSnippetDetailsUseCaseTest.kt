package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.UserDirectoryUnavailable
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.FakeUserDirectory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GetSnippetDetailsUseCaseTest {
    @Mock
    private lateinit var repository: SnippetQueryRepository

    private val snippetId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val snippet =
        Snippet.create(snippetId, "s", "d", "printscript", "1.1", OWNER, "println(1);", versionId, Instant.EPOCH)
    private val analysisValue =
        SnippetAnalysis(
            ValidationStatus.VALID,
            null,
            LintStatus.NOT_COMPLIANT,
            listOf(CodeIssue("IdentifierFormat", "Use camel case", null)),
            FormatStatus.FINISHED,
        )
    private val analysis =
        FakeSnippetAnalysisRepository().apply { addSnippet(snippetId, versionId, OWNER, analysisValue) }
    private val permissions = FakeSnippetPermissions()
    private val directory = FakeUserDirectory(names = mapOf(OWNER to "Olga Owner"))

    private val useCase by lazy { GetSnippetDetailsUseCase(repository, analysis, permissions, directory) }

    @Test
    fun `a reader sees the snippet, the owner's name and the analysis`() {
        Mockito.`when`(repository.findById(snippetId)).thenReturn(Result.Success(snippet))

        val result = useCase.execute(GetSnippetQuery(snippetId, "auth0|reader"))

        val expected = SnippetDetails(snippet, "Olga Owner", isOwner = false, analysis = analysisValue)
        assertEquals(Result.Success(expected), result)
    }

    @Test
    fun `the owner is told so`() {
        Mockito.`when`(repository.findById(snippetId)).thenReturn(Result.Success(snippet))

        val details = (useCase.execute(GetSnippetQuery(snippetId, OWNER)) as Result.Success).value

        assertEquals(true, details.isOwner)
    }

    @Test
    fun `a directory outage only costs the display name`() {
        directory.failure = UserDirectoryUnavailable("down")
        Mockito.`when`(repository.findById(snippetId)).thenReturn(Result.Success(snippet))

        val details = (useCase.execute(GetSnippetQuery(snippetId, OWNER)) as Result.Success).value

        assertEquals(OWNER, details.ownerName)
    }

    @Test
    fun `without READ it is 403 and nothing is loaded`() {
        permissions.allowed = false

        assertEquals(Result.Failure(AccessDenied(snippetId, "x")), useCase.execute(GetSnippetQuery(snippetId, "x")))
        Mockito.verifyNoInteractions(repository)
    }

    private companion object {
        const val OWNER = "auth0|owner"
    }
}
