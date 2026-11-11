package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.application.command.common.storeThenPersist
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.query.SnippetMetadata
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class UpdateSnippetUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
    private val clock: Clock,
) : UpdateSnippet {
    override fun execute(command: UpdateSnippetCommand): Result<SnippetError, Snippet> =

        snippetQueryRepository.findMetadataById(command.id).flatMap { existing ->
            val newVersionId = UUID.randomUUID()
            val updatedSnippet = buildUpdatedSnippet(command, existing, newVersionId)
            val contentKey = "snippets/${command.id}/versions/$newVersionId/original"

            contentStorage.storeThenPersist(
                key = contentKey,
                content = command.content,
            ) {
                snippetRepository.update(
                    snippet = updatedSnippet,
                    originalContentKey = contentKey,
                )
            }
        }

    private fun buildUpdatedSnippet(
        command: UpdateSnippetCommand,
        existing: SnippetMetadata,
        newVersionId: UUID,
    ): Snippet =
        Snippet(
            id = command.id,
            name = command.name,
            description = command.description,
            language = command.language,
            languageVersion = command.languageVersion,
            owner = existing.owner,
            currentVersion =
                SnippetVersion(
                    id = newVersionId,
                    snippetId = command.id,
                    versionNumber = existing.currentVersionNumber + 1,
                    originalContent = command.content,
                    formattedContent = null,
                    createdAt = Instant.now(clock),
                ),
        )
}
