package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import org.springframework.stereotype.Service

@Service
class GetSnippetDetailsUseCase(
    private val repository: SnippetQueryRepository,
    private val analysisRepository: SnippetAnalysisRepository,
    private val permissions: SnippetPermissions,
    private val userDirectory: UserDirectory,
) : GetSnippetDetails {
    override fun execute(query: GetSnippetQuery): Result<SnippetError, SnippetDetails> =
        permissions
            .authorize(query.snippetId, query.userId, SnippetAction.READ)
            .flatMap { repository.findById(query.snippetId) }
            .flatMap { snippet ->
                analysisRepository.find(snippet.currentVersion.id).map { analysis ->
                    SnippetDetails(
                        snippet = snippet,
                        ownerName = ownerName(snippet.owner),
                        isOwner = snippet.owner == query.userId,
                        analysis = analysis,
                    )
                }
            }

    /** A directory outage only costs the display name. */
    private fun ownerName(owner: String): String =
        userDirectory
            .namesOf(setOf(owner))
            .fold(onSuccess = { it[owner] ?: owner }, onFailure = { owner })
}
