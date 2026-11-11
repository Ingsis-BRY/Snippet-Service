package com.ingsisbry.snippet.application.query.download

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DownloadSnippetUseCase(
    private val snippetQueryRepository: SnippetQueryRepository,
) : DownloadSnippet {
    override fun execute(
        id: UUID,
        variant: SnippetContentVariant,
    ): Result<SnippetError, String> =
        snippetQueryRepository.findById(id).map { snippet ->
            when (variant) {
                SnippetContentVariant.ORIGINAL ->
                    getOriginalContent(snippet)

                SnippetContentVariant.FORMATTED ->
                    getFormattedContent(snippet)
            }
        }

    private fun getOriginalContent(snippet: Snippet): String = snippet.currentVersion.originalContent

    private fun getFormattedContent(snippet: Snippet): String = snippet.currentVersion.originalContent
}
