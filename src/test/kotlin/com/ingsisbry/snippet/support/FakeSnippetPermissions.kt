package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import java.util.UUID

/** In-memory [SnippetPermissions]: allows everything unless told otherwise, and records what it was asked. */
class FakeSnippetPermissions(
    var allowed: Boolean = true,
    var accessibleIds: Set<UUID> = emptySet(),
    var failure: SnippetError? = null,
) : SnippetPermissions {
    val owners = mutableMapOf<UUID, String>()
    val shares = mutableListOf<Triple<UUID, String, String>>()
    val removed = mutableListOf<UUID>()
    val checks = mutableListOf<Triple<UUID, String, SnippetAction>>()
    var lastRelationship: SnippetRelationship? = null

    override fun registerOwner(
        snippetId: UUID,
        userId: String,
    ): Result<SnippetError, Unit> = respond { owners[snippetId] = userId }

    override fun isAllowed(
        snippetId: UUID,
        userId: String,
        action: SnippetAction,
    ): Result<SnippetError, Boolean> =
        respond {
            checks += Triple(snippetId, userId, action)
            allowed
        }

    override fun accessibleSnippetIds(
        userId: String,
        relationship: SnippetRelationship,
    ): Result<SnippetError, Set<UUID>> =
        respond {
            lastRelationship = relationship
            accessibleIds
        }

    override fun share(
        snippetId: UUID,
        ownerId: String,
        targetUserId: String,
    ): Result<SnippetError, Unit> = respond { shares += Triple(snippetId, ownerId, targetUserId) }

    override fun removeAll(
        snippetId: UUID,
        requesterId: String,
    ): Result<SnippetError, Unit> = respond { removed += snippetId }

    private fun <T> respond(action: () -> T): Result<SnippetError, T> {
        val error = failure
        return if (error != null) Result.Failure(error) else Result.Success(action())
    }
}
