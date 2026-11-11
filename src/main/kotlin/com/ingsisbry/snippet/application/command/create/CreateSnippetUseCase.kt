package com.ingsisbry.snippet.application.command.create

import com.ingsisbry.snippet.application.command.common.storeThenPersist
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.language.SnippetContentValidator
import com.ingsisbry.snippet.application.port.out.language.SupportedLanguages
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetRules
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class CreateSnippetUseCase(
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
    private val supportedLanguages: SupportedLanguages,
    private val contentValidator: SnippetContentValidator,
    private val clock: Clock,
) : CreateSnippet {
    override fun execute(command: CreateSnippetCommand): Result<SnippetError, Snippet> {
        val violation =
            SnippetRules.violation(
                name = command.name,
                description = command.description,
                language = command.language,
                languageVersion = command.languageVersion,
                content = command.content,
            )

        if (violation != null) {
            return Result.Failure(InvalidInput(violation))
        }

        return supportedLanguages
            .check(command.language, command.languageVersion)
            .flatMap { contentValidator.validate(command.language, command.languageVersion, command.content) }
            .flatMap { store(command) }
    }

    private fun store(command: CreateSnippetCommand): Result<SnippetError, Snippet> {
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

        return contentStorage.storeThenPersist(
            key = originalContentKey,
            content = snippet.currentVersion.originalContent,
        ) {
            snippetRepository.save(
                snippet = snippet,
                originalContentKey = originalContentKey,
            )
        }
    }
}
