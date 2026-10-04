package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import java.time.Clock
import java.time.Instant
import java.util.UUID

class CreateSnippetUseCase(
    private val snippetRepository: SnippetRepository,
    private val clock: Clock,
    private val idGenerator: () -> UUID = UUID::randomUUID,
) : CreateSnippet {

    override fun execute(command: CreateSnippetCommand): Snippet {
        val snippetId = idGenerator()
        val versionId = idGenerator()

        val snippet = Snippet.create(
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

        return snippetRepository.save(snippet)
    }
}