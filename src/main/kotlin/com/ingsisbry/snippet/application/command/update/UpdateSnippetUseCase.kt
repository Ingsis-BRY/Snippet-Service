package com.ingsisbry.snippet.application.command.update

import com.ingsisbry.snippet.adapter.web.dto.request.UpdateSnippetRequest
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class UpdateSnippetUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
) : UpdateSnippet {
    override fun execute(
        id: UUID,
        request: UpdateSnippetRequest,
    ): Snippet? {
        val existingSnippet = snippetQueryRepository.findById(id) ?: return null

        val newVersionId = UUID.randomUUID()
        val newVersionNumber = existingSnippet.currentVersion.versionNumber + 1

        val newVersion =
            SnippetVersion(
                id = newVersionId,
                snippetId = id,
                versionNumber = newVersionNumber,
                originalContent = request.content,
                formattedContent = null,
                createdAt = Instant.now(),
            )

        val updatedSnippet =
            existingSnippet.copy(
                name = request.name,
                description = request.description,
                language = request.language,
                languageVersion = request.languageVersion,
                currentVersion = newVersion,
            )

        val contentKey = "snippets/$id/versions/$newVersionId/original"

        contentStorage.store(contentKey, request.content)

        return try {
            snippetRepository.update(
                snippet = updatedSnippet,
                originalContentKey = contentKey,
            )
        } catch (exception: DataIntegrityViolationException) {
            runCatching { contentStorage.delete(contentKey) }
            throw exception
        }
    }
}
