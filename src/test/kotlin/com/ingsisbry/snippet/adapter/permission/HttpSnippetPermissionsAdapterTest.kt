package com.ingsisbry.snippet.adapter.permission

import com.ingsisbry.snippet.adapter.security.BearerTokenForwarder
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.json.JsonCompareMode
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
    private val builder =
        RestClient
            .builder()
            .baseUrl(BASE_URL)
            .requestInterceptor(BearerTokenForwarder())
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val adapter = HttpSnippetPermissionsAdapter(builder.build())
    private val snippetId = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun authenticate() {
        val jwt =
            Jwt
                .withTokenValue(TOKEN)
                .header("alg", "RS256")
                .subject("auth0|franco")
                .build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
    }

    @AfterEach
    fun verify() {
        SecurityContextHolder.clearContext()
        server.verify()
    }

    @Test
    fun `registerOwner posts the snippet and forwards the user's token`() {
        server
            .expect(requestTo("$BASE_URL/permissions/owner"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN"))
            .andExpect(content().json("""{"snippetId":"$snippetId"}""", JsonCompareMode.STRICT))
            .andRespond(withStatus(HttpStatus.CREATED))

        assertEquals(Result.Success(Unit), adapter.registerOwner(snippetId, "auth0|franco"))
    }

    @Test
    fun `registerOwner maps a conflict to a snippet conflict`() {
        server
            .expect(requestTo("$BASE_URL/permissions/owner"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertEquals(Result.Failure(SnippetConflict(snippetId)), adapter.registerOwner(snippetId, "auth0|franco"))
    }

    @Test
    fun `isAllowed asks for the token's user without sending a user id`() {
        server
            .expect(requestTo("$BASE_URL/permissions/check?snippetId=$snippetId&action=WRITE"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN"))
            .andRespond(withSuccess("""{"allowed":false}""", MediaType.APPLICATION_JSON))

        assertEquals(Result.Success(false), adapter.isAllowed(snippetId, "auth0|franco", SnippetAction.WRITE))
    }

    @Test
    fun `accessibleSnippetIds translates OWNED to the OWNER relationship of the permission service`() {
        val other = UUID.randomUUID()
        server
            .expect(requestTo("$BASE_URL/permissions/snippets?relationship=OWNER"))
            .andRespond(
                withSuccess("""{"snippetIds":["$snippetId","$other"]}""", MediaType.APPLICATION_JSON),
            )

        assertEquals(
            Result.Success(setOf(snippetId, other)),
            adapter.accessibleSnippetIds("auth0|franco", SnippetRelationship.OWNED),
        )
    }

    @Test
    fun `share sends the target user and the requester's token`() {
        server
            .expect(requestTo("$BASE_URL/permissions/share"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN"))
            .andExpect(
                content().json("""{"snippetId":"$snippetId","targetUserId":"auth0|enzo"}""", JsonCompareMode.STRICT),
            ).andRespond(withStatus(HttpStatus.CREATED))

        assertEquals(Result.Success(Unit), adapter.share(snippetId, "auth0|franco", "auth0|enzo"))
    }

    @Test
    fun `share maps the answers of the permission service to snippet errors`() {
        val cases =
            listOf(
                HttpStatus.FORBIDDEN to AccessDenied(snippetId, "auth0|franco"),
                HttpStatus.CONFLICT to AlreadyShared(snippetId, "auth0|enzo"),
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
            assertEquals(Result.Failure(expected), adapter.share(snippetId, "auth0|franco", "auth0|enzo"))
        }
    }

    @Test
    fun `removeAll deletes every permission of the snippet with the requester's token`() {
        server
            .expect(requestTo("$BASE_URL/permissions/snippets/$snippetId"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer $TOKEN"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT))

        assertEquals(Result.Success(Unit), adapter.removeAll(snippetId, "auth0|franco"))
    }

    @Test
    fun `a rejected token is reported as an unavailable permission service`() {
        server
            .expect(requestTo("$BASE_URL/permissions/check?snippetId=$snippetId&action=READ"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        val result = adapter.isAllowed(snippetId, "auth0|franco", SnippetAction.READ)

        assertTrue((result as Result.Failure).error is PermissionServiceUnavailable)
    }

    @Test
    fun `a server error is reported as an unavailable permission service`() {
        server
            .expect(requestTo("$BASE_URL/permissions/check?snippetId=$snippetId&action=READ"))
            .andRespond(withServerError())

        val result = adapter.isAllowed(snippetId, "auth0|franco", SnippetAction.READ)

        assertTrue((result as Result.Failure).error is PermissionServiceUnavailable)
    }

    private companion object {
        const val BASE_URL = "http://permission-service"
        const val TOKEN = "user-access-token"
    }
}
