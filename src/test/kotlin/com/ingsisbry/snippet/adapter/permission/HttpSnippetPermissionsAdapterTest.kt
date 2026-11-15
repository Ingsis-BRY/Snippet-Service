package com.ingsisbry.snippet.adapter.permission

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.AlreadyShared
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.PermissionServiceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.query.list.SnippetRelationship
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID

class HttpSnippetPermissionsAdapterTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = HttpSnippetPermissionsAdapter(builder.build())
    private val snippetId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @AfterEach
    fun verify() {
        server.verify()
    }

    @Test
    fun `registerOwner posts the owner of the snippet`() {
        server
            .expect(requestTo("$BASE_URL/permissions/owner"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"snippetId":"$snippetId","userId":"franco"}"""))
            .andRespond(withStatus(HttpStatus.CREATED))

        assertEquals(Result.Success(Unit), adapter.registerOwner(snippetId, "franco"))
    }

    @Test
    fun `registerOwner maps a conflict to a snippet conflict`() {
        server
            .expect(requestTo("$BASE_URL/permissions/owner"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertEquals(Result.Failure(SnippetConflict(snippetId)), adapter.registerOwner(snippetId, "franco"))
    }

    @Test
    fun `isAllowed reads the answer of the check endpoint`() {
        server
            .expect(requestTo("$BASE_URL/permissions/check?snippetId=$snippetId&userId=enzo&action=WRITE"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"allowed":false}""", MediaType.APPLICATION_JSON))

        assertEquals(Result.Success(false), adapter.isAllowed(snippetId, "enzo", SnippetAction.WRITE))
    }

    @Test
    fun `accessibleSnippetIds translates OWNED to the OWNER relationship of the permission service`() {
        val other = UUID.randomUUID()
        server
            .expect(requestTo("$BASE_URL/permissions/snippets?userId=franco&relationship=OWNER"))
            .andRespond(
                withSuccess("""{"snippetIds":["$snippetId","$other"]}""", MediaType.APPLICATION_JSON),
            )

        assertEquals(
            Result.Success(setOf(snippetId, other)),
            adapter.accessibleSnippetIds("franco", SnippetRelationship.OWNED),
        )
    }

    @Test
    fun `share sends the requester header and the target user`() {
        server
            .expect(requestTo("$BASE_URL/permissions/share"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-User-Id", "franco"))
            .andExpect(content().json("""{"snippetId":"$snippetId","targetUserId":"enzo"}"""))
            .andRespond(withStatus(HttpStatus.CREATED))

        assertEquals(Result.Success(Unit), adapter.share(snippetId, "franco", "enzo"))
    }

    @Test
    fun `share maps the answers of the permission service to snippet errors`() {
        val cases =
            listOf(
                HttpStatus.FORBIDDEN to AccessDenied(snippetId, "franco"),
                HttpStatus.CONFLICT to AlreadyShared(snippetId, "enzo"),
                HttpStatus.BAD_REQUEST to InvalidInput("Cannot share a snippet with yourself"),
            )
        cases.forEach { (status, _) ->
            server
                .expect(requestTo("$BASE_URL/permissions/share"))
                .andRespond(
                    withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""{"message":"Cannot share a snippet with yourself"}"""),
                )
        }

        cases.forEach { (_, expected) ->
            assertEquals(Result.Failure(expected), adapter.share(snippetId, "franco", "enzo"))
        }
    }

    @Test
    fun `removeAll deletes every permission of the snippet as the requester`() {
        server
            .expect(requestTo("$BASE_URL/permissions/snippets/$snippetId"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header("X-User-Id", "franco"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        assertEquals(Result.Success(Unit), adapter.removeAll(snippetId, "franco"))
    }

    @Test
    fun `a server error is reported as an unavailable permission service`() {
        server
            .expect(requestTo("$BASE_URL/permissions/check?snippetId=$snippetId&userId=enzo&action=READ"))
            .andRespond(withServerError())

        val result = adapter.isAllowed(snippetId, "enzo", SnippetAction.READ)

        assertTrue((result as Result.Failure).error is PermissionServiceUnavailable)
    }

    private companion object {
        const val BASE_URL = "http://permission-service"
    }
}
