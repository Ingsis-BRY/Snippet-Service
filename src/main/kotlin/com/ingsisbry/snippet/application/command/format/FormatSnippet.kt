package com.ingsisbry.snippet.application.command.format

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import java.util.UUID

/** [content] null = the snippet's current content. */
data class FormatSnippetCommand(
    val snippetId: UUID,
    val userId: String,
    val content: String?,
)

/** The editor's Format button: formats without storing anything. */
fun interface FormatSnippet {
    fun execute(command: FormatSnippetCommand): Result<SnippetError, FormattedSnippet>
}
