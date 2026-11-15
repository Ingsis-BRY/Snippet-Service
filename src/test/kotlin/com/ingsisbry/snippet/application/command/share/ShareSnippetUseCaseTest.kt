package com.ingsisbry.snippet.application.command.share

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ShareSnippetUseCaseTest {
    private val permissions = FakeSnippetPermissions()
    private val useCase = ShareSnippetUseCase(permissions)
    private val snippetId = UUID.randomUUID()

    @Test
    fun `execute should share the snippet when the requester is the owner`() {
        val result = useCase.execute(ShareSnippetCommand(snippetId, "franco", "enzo"))

        assertEquals(Result.Success(Unit), result)
        assertEquals(listOf(Triple(snippetId, "franco", SnippetAction.SHARE)), permissions.checks)
        assertEquals(listOf(Triple(snippetId, "franco", "enzo")), permissions.shares)
    }

    @Test
    fun `execute should deny sharing when the requester is not the owner`() {
        permissions.allowed = false

        val result = useCase.execute(ShareSnippetCommand(snippetId, "enzo", "bruno"))

        assertEquals(Result.Failure(AccessDenied(snippetId, "enzo")), result)
        assertTrue(permissions.shares.isEmpty())
    }

    @Test
    fun `execute should reject a blank target user without calling the permission service`() {
        val result = useCase.execute(ShareSnippetCommand(snippetId, "franco", " "))

        assertEquals(Result.Failure(InvalidInput("targetUserId must not be blank")), result)
        assertTrue(permissions.checks.isEmpty())
    }
}
