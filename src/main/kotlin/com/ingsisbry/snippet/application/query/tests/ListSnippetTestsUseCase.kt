package com.ingsisbry.snippet.application.query.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ListSnippetTestsUseCase(
    private val repository: SnippetTestRepository,
    private val permissions: SnippetPermissions,
) : ListSnippetTests {
    override fun execute(
        snippetId: UUID,
        userId: String,
    ): Result<SnippetError, List<SnippetTestView>> =
        permissions
            .authorize(snippetId, userId, SnippetAction.READ)
            .map { repository.findAll(snippetId) }
}
