package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteSnippetTestUseCase(
    private val repository: SnippetTestRepository,
    private val permissions: SnippetPermissions,
) : DeleteSnippetTest {
    override fun execute(
        snippetId: UUID,
        testId: UUID,
        userId: String,
    ): Result<SnippetError, Unit> =
        permissions
            .authorize(snippetId, userId, SnippetAction.WRITE)
            .flatMap { repository.delete(snippetId, testId) }
}
