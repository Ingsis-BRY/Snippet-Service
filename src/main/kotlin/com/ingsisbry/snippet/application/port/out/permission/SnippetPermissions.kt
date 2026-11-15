package com.ingsisbry.snippet.application.port.out.permission

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import java.util.UUID

/** Who can do what with each snippet. Owned by the permission service. */
interface SnippetPermissions {
    fun registerOwner(
        snippetId: UUID,
        userId: String,
    ): Result<SnippetError, Unit>

    fun isAllowed(
        snippetId: UUID,
        userId: String,
        action: SnippetAction,
    ): Result<SnippetError, Boolean>

    fun accessibleSnippetIds(
        userId: String,
        relationship: SnippetRelationship,
    ): Result<SnippetError, Set<UUID>>

    fun share(
        snippetId: UUID,
        ownerId: String,
        targetUserId: String,
    ): Result<SnippetError, Unit>

    /** Removes every permission of the snippet. Only its owner can do it. */
    fun removeAll(
        snippetId: UUID,
        requesterId: String,
    ): Result<SnippetError, Unit>
}

/** Succeeds only if [userId] can perform [action]; otherwise fails with [AccessDenied]. */
fun SnippetPermissions.authorize(
    snippetId: UUID,
    userId: String,
    action: SnippetAction,
): Result<SnippetError, Unit> =
    isAllowed(snippetId, userId, action).flatMap { allowed ->
        if (allowed) Result.Success(Unit) else Result.Failure(AccessDenied(snippetId, userId))
    }
