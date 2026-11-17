package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.domain.test.SnippetTest
import com.ingsisbry.snippet.domain.test.SnippetTestRules
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class CreateSnippetTestUseCase(
    private val repository: SnippetTestRepository,
    private val writer: SnippetTestWriter,
    private val permissions: SnippetPermissions,
    private val clock: Clock,
) : CreateSnippetTest {
    override fun execute(command: CreateSnippetTestCommand): Result<SnippetError, SnippetTestView> {
        val definition = command.definition
        val violation = SnippetTestRules.violation(definition.name, definition.inputs, definition.expectedOutputs)
        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            permissions
                .authorize(command.snippetId, command.userId, SnippetAction.WRITE)
                .flatMap { checkRoom(command) }
                .flatMap {
                    writer.save(
                        SnippetTest(
                            id = UUID.randomUUID(),
                            snippetId = command.snippetId,
                            name = definition.name,
                            inputs = definition.inputs,
                            expectedOutputs = definition.expectedOutputs,
                            createdAt = Instant.now(clock),
                        ),
                    )
                }
        }
    }

    private fun checkRoom(command: CreateSnippetTestCommand): Result<SnippetError, Unit> {
        val violation = SnippetTestRules.countViolation(repository.count(command.snippetId))
        return if (violation == null) Result.Success(Unit) else Result.Failure(InvalidInput(violation))
    }
}
