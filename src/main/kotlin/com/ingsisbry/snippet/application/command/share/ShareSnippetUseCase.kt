package com.ingsisbry.snippet.application.command.share

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.port.out.permission.SnippetPermissions
import com.ingsisbry.snippet.application.port.out.permission.authorize
import org.springframework.stereotype.Service

/** Gives another user read access to a snippet. Only its owner can do it. */
@Service
class ShareSnippetUseCase(
    private val permissions: SnippetPermissions,
) : ShareSnippet {
    override fun execute(command: ShareSnippetCommand): Result<SnippetError, Unit> =
        if (command.targetUserId.isBlank()) {
            Result.Failure(InvalidInput("targetUserId must not be blank"))
        } else {
            permissions
                .authorize(command.snippetId, command.ownerId, SnippetAction.SHARE)
                .flatMap { permissions.share(command.snippetId, command.ownerId, command.targetUserId) }
        }
}
