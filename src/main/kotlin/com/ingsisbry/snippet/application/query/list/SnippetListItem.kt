package com.ingsisbry.snippet.application.query.list

import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.ProcessingStatus
import java.util.UUID

/** [ownerName] is the owner's display name, or the owner id when the directory doesn't know it. */
data class SnippetListItem(
    val id: UUID,
    val name: String,
    val description: String,
    val language: String,
    val languageVersion: String,
    val owner: String,
    val ownerName: String,
    val compliance: LintStatus,
    val processing: ProcessingStatus,
)
