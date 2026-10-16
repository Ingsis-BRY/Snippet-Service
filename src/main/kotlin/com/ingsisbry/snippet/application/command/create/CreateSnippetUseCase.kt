package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class CreateSnippetUseCase(
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
    private val clock: Clock,
) : CreateSnippet {
    override fun execute(command: CreateSnippetCommand): Snippet {
        val snippetId = UUID.randomUUID()
        val versionId = UUID.randomUUID()

        val snippet =
            Snippet.create(
                id = snippetId,
                name = command.name,
                description = command.description,
                language = command.language,
                languageVersion = command.languageVersion,
                owner = command.owner,
                content = command.content,
                versionId = versionId,
                createdAt = Instant.now(clock),
            )

        val originalContentKey = "snippets/$snippetId/versions/$versionId/original"

        contentStorage.store(
            key = originalContentKey,
            content = snippet.currentVersion.originalContent,
        )

        try {
            return snippetRepository.save(
                snippet = snippet,
                originalContentKey = originalContentKey,
            )
        } catch (exception: DataIntegrityViolationException) {
            // cleanup if persistence of metadata in DB fails.
            runCatching {
                contentStorage.delete(originalContentKey)
            }

            throw exception
        }
    }
}
