package com.ingsisbry.snippet.adapter.permission

import java.util.UUID

internal data class GrantOwnerRequest(
    val snippetId: UUID,
    val userId: String,
)

internal data class ShareRequest(
    val snippetId: UUID,
    val targetUserId: String,
)

internal data class CheckResponse(
    val allowed: Boolean,
)

internal data class SnippetIdsResponse(
    val snippetIds: Set<UUID>,
)

internal data class ErrorResponse(
    val message: String?,
)
