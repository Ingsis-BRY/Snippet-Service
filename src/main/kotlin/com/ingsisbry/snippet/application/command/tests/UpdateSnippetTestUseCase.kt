package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.domain.test.SnippetTestRules
import org.springframework.stereotype.Service

@Service
class UpdateSnippetTestUseCase(
    private val repository: SnippetTestRepository,
    private val writer: SnippetTestWriter,
    private val permissions: SnippetPermissions,
) : UpdateSnippetTest {
    override fun execute(command: UpdateSnippetTestCommand): Result<SnippetError, SnippetTestView> {
        val definition = command.definition
        val violation = SnippetTestRules.violation(definition.name, definition.inputs, definition.expectedOutputs)
        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            permissions
                .authorize(command.snippetId, command.userId, SnippetAction.WRITE)
                .flatMap {
                    val existing = repository.find(command.snippetId, command.testId)
                    if (existing == null) {
                        Result.Failure(TestNotFound(command.snippetId, command.testId))
                    } else {
                        writer.save(
                            existing.test.copy(
                                name = definition.name,
                                inputs = definition.inputs,
                                expectedOutputs = definition.expectedOutputs,
                            ),
                        )
                    }
                }
        }
    }
}
