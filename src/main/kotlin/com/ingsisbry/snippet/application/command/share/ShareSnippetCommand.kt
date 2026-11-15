package com.ingsisbry.snippet.application.command.share

import java.util.UUID

data class ShareSnippetCommand(
    val snippetId: UUID,
    val ownerId: String,
    val targetUserId: String,
)
