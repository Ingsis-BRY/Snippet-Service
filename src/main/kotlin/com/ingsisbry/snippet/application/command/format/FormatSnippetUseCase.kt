package com.ingsisbry.snippet.application.command.format

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.SnippetRules
import org.springframework.stereotype.Service

/** Anyone who can read the snippet can format it; the result uses the owner's rules and is never stored. */
@Service
class FormatSnippetUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val permissions: SnippetPermissions,
    private val ownerFormatter: OwnerFormatter,
) : FormatSnippet {
    override fun execute(command: FormatSnippetCommand): Result<SnippetError, FormattedSnippet> {
        val violation = command.content?.let { SnippetRules.contentViolation(it) }
        return if (violation != null) {
            Result.Failure(InvalidInput(violation))
        } else {
            permissions
                .authorize(command.snippetId, command.userId, SnippetAction.READ)
                .flatMap { format(command) }
        }
    }

    private fun format(command: FormatSnippetCommand): Result<SnippetError, FormattedSnippet> {
        val content = command.content
        return if (content == null) {
            snippetQueryRepository.findById(command.snippetId).flatMap { snippet ->
                ownerFormatter.format(
                    snippet.owner,
                    snippet.language,
                    snippet.languageVersion,
                    snippet.currentVersion.originalContent,
                )
            }
        } else {
            snippetQueryRepository.findMetadataById(command.snippetId).flatMap { metadata ->
                ownerFormatter.format(metadata.owner, metadata.language, metadata.languageVersion, content)
            }
        }
    }
}
