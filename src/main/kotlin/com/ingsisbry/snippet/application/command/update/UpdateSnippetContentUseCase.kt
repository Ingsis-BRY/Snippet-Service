package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.application.command.common.storeThenPersist
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetRules
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class UpdateSnippetContentUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
    private val contentValidator: SnippetContentValidator,
    private val clock: Clock,
) : UpdateSnippetContent {
    override fun execute(command: UpdateSnippetContentCommand): Result<SnippetError, Snippet> {
        val violation = SnippetRules.contentViolation(command.content)

        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            snippetQueryRepository
                .findMetadataById(command.id)
                .flatMap { existing ->
                    contentValidator
                        .validate(existing.language, existing.languageVersion, command.content)
                        .flatMap { store(command, existing) }
                }
        }
    }

    private fun store(
        command: UpdateSnippetContentCommand,
        existing: SnippetMetadata,
    ): Result<SnippetError, Snippet> {
        val newVersionId = UUID.randomUUID()
        val updatedSnippet =
            Snippet.update(
                id = command.id,
                owner = existing.owner,
                previousVersionNumber = existing.currentVersionNumber,
                name = existing.name,
                description = existing.description,
                language = existing.language,
                languageVersion = existing.languageVersion,
                content = command.content,
                versionId = newVersionId,
                createdAt = Instant.now(clock),
            )
        val contentKey = "snippets/${command.id}/versions/$newVersionId/original"

        return contentStorage.storeThenPersist(key = contentKey, content = command.content) {
            snippetRepository.update(snippet = updatedSnippet, originalContentKey = contentKey)
        }
    }
}
