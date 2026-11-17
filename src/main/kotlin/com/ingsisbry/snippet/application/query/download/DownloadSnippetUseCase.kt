package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.command.format.OwnerFormatter
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DownloadSnippetUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
    private val permissions: SnippetPermissions,
    private val ownerFormatter: OwnerFormatter,
) : DownloadSnippet {
    override fun execute(
        id: UUID,
        userId: String,
        variant: SnippetContentVariant,
    ): Result<SnippetError, String> =
        permissions.authorize(id, userId, SnippetAction.READ).flatMap {
            download(id, variant)
        }

    private fun download(
        id: UUID,
        variant: SnippetContentVariant,
    ): Result<SnippetError, String> =
        snippetQueryRepository.findById(id).flatMap { snippet ->
            when (variant) {
                SnippetContentVariant.ORIGINAL -> Result.Success(snippet.currentVersion.originalContent)
                SnippetContentVariant.FORMATTED -> formattedContent(snippet)
            }
        }

    /**
     * The worker stores the formatted version in the background (US#13). Until it has, the snippet is formatted
     * now with its owner's rules, and that result is not stored.
     */
    private fun formattedContent(snippet: Snippet): Result<SnippetError, String> =
        snippet.currentVersion.formattedContent
            ?.let { Result.Success(it) }
            ?: ownerFormatter
                .format(
                    snippet.owner,
                    snippet.language,
                    snippet.languageVersion,
                    snippet.currentVersion.originalContent,
                ).map { it.formatted }
}
