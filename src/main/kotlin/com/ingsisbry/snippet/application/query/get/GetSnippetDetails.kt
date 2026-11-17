package com.ingsisbry.snippet.application.query.get

import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.domain.Snippet

/** A snippet as its detail page shows it (US#6): content, owner, and what the analysis found. */
data class SnippetDetails(
    val snippet: Snippet,
    val ownerName: String,
    val isOwner: Boolean,
    val analysis: SnippetAnalysis,
)

fun interface GetSnippetDetails {
    fun execute(query: GetSnippetQuery): Result<SnippetError, SnippetDetails>
}
