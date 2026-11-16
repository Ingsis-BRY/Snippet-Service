package com.ingsisbry.snippet.adapter.security

import com.ingsisbry.snippet.adapter.web.SnippetSharingController
import com.ingsisbry.snippet.adapter.web.UserController
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.share.ShareSnippet
import com.ingsisbry.snippet.application.command.share.ShareSnippetCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.query.user.SearchUsers
import com.ingsisbry.snippet.application.query.user.SearchUsersQuery
import com.ingsisbry.snippet.application.query.user.UserPage
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import java.util.UUID

@WebMvcTest(
    controllers = [UserController::class, SnippetSharingController::class],
    properties = ["AUTH0_DOMAIN=auth0.test.invalid", "AUTH0_AUDIENCE=https://snippet-searcher-api"],
)
@Import(SecurityConfiguration::class, WebConfiguration::class, SnippetErrorMapper::class)
class SecurityConfigurationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var searchUsers: SearchUsers

    @MockitoBean
    private lateinit var shareSnippet: ShareSnippet

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    private val snippetId = UUID.randomUUID()

    private fun token(vararg scopes: String): RequestPostProcessor =
        jwt()
            .jwt { it.subject(USER) }
            .authorities(scopes.map { SimpleGrantedAuthority("SCOPE_$it") })

    @Test
    fun `a request without a token is rejected with 401`() {
        mockMvc.get("/users").andExpect { status { isUnauthorized() } }
        Mockito.verifyNoInteractions(searchUsers)
    }

    @Test
    fun `a read needs the read scope`() {
        mockMvc
            .get("/users") { with(token(WRITE)) }
            .andExpect { status { isForbidden() } }
        Mockito.verifyNoInteractions(searchUsers)
    }

    @Test
    fun `the caller is the token's subject`() {
        Mockito
            .`when`(searchUsers.execute(SearchUsersQuery(requesterId = USER, name = "usu")))
            .thenReturn(Result.Success(UserPage(emptyList(), page = 0, size = 10, total = 0)))

        mockMvc
            .get("/users") {
                with(token(READ))
                param("name", "usu")
            }.andExpect { status { isOk() } }

        Mockito.verify(searchUsers).execute(SearchUsersQuery(requesterId = USER, name = "usu"))
    }

    @Test
    fun `a write needs the write scope`() {
        mockMvc
            .post("/snippets/$snippetId/share") {
                with(token(READ))
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetUserId":"auth0|enzo"}"""
            }.andExpect { status { isForbidden() } }
        Mockito.verifyNoInteractions(shareSnippet)
    }

    @Test
    fun `a write with the write scope acts as the token's subject`() {
        val command = ShareSnippetCommand(snippetId, ownerId = USER, targetUserId = "auth0|enzo")
        Mockito.`when`(shareSnippet.execute(command)).thenReturn(Result.Success(Unit))

        mockMvc
            .post("/snippets/$snippetId/share") {
                with(token(READ, WRITE))
                contentType = MediaType.APPLICATION_JSON
                content = """{"targetUserId":"auth0|enzo"}"""
            }.andExpect { status { isNoContent() } }

        Mockito.verify(shareSnippet).execute(command)
    }

    private companion object {
        const val USER = "auth0|franco"
        const val READ = "read:snippets"
        const val WRITE = "write:snippets"
    }
}
