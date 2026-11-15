package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.web.dto.request.ShareSnippetRequest
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.share.ShareSnippet
import com.ingsisbry.snippet.application.command.share.ShareSnippetCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.AlreadyShared
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import java.util.UUID

class ShareSnippetEndpointTest {
    private val shareSnippet = Mockito.mock(ShareSnippet::class.java)
    private val controller = SnippetSharingController(shareSnippet, SnippetErrorMapper())
    private val snippetId = UUID.randomUUID()

    @Test
    fun `share returns 204 when the owner shares the snippet`() {
        val command = ShareSnippetCommand(snippetId, "franco", "enzo")
        Mockito.`when`(shareSnippet.execute(command)).thenReturn(Result.Success(Unit))

        val response = controller.share("franco", snippetId, ShareSnippetRequest("enzo"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        Mockito.verify(shareSnippet).execute(command)
    }

    @Test
    fun `share returns 403 when the requester is not the owner`() {
        val command = ShareSnippetCommand(snippetId, "enzo", "bruno")
        Mockito
            .`when`(shareSnippet.execute(command))
            .thenReturn(Result.Failure(AccessDenied(snippetId, "enzo")))

        val response = controller.share("enzo", snippetId, ShareSnippetRequest("bruno"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `share returns 409 when the snippet is already shared with that user`() {
        val command = ShareSnippetCommand(snippetId, "franco", "enzo")
        Mockito
            .`when`(shareSnippet.execute(command))
            .thenReturn(Result.Failure(AlreadyShared(snippetId, "enzo")))

        val response = controller.share("franco", snippetId, ShareSnippetRequest("enzo"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
