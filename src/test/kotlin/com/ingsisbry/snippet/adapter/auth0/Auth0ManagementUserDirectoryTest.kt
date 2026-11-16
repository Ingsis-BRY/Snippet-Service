package com.ingsisbry.snippet.adapter.auth0

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.UserDirectoryUnavailable
import com.ingsisbry.snippet.application.query.user.UserPage
import com.ingsisbry.snippet.application.query.user.UserSummary
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.RequestMatcher
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class Auth0ManagementUserDirectoryTest {
    private val builder = RestClient.builder().baseUrl(BASE_URL)
    private val server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build()
    private val client = builder.build()
    private val tokens = ManagementApiTokens(client, "client-id", "client-secret", "$BASE_URL/api/v2/")
    private val directory = Auth0ManagementUserDirectory(client, tokens)

    @AfterEach
    fun verify() {
        server.verify()
    }

    private fun expectToken(count: ExpectedCount = ExpectedCount.once()) {
        server
            .expect(count, requestTo("$BASE_URL/oauth/token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().json(
                    """{"grant_type":"client_credentials","client_id":"client-id",
                    "client_secret":"client-secret","audience":"$BASE_URL/api/v2/"}""",
                ),
            ).andRespond(
                withSuccess("""{"access_token":"m2m-token","expires_in":86400}""", MediaType.APPLICATION_JSON),
            )
    }

    @Test
    fun `search queries the Management API with a cached machine-to-machine token`() {
        expectToken(ExpectedCount.once())
        server
            .expect(ExpectedCount.twice(), requestTo(org.hamcrest.Matchers.startsWith("$BASE_URL/api/v2/users")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer m2m-token"))
            .andExpect(decodedQueryParam("q", "(name:*dos* OR email:*dos*) AND NOT user_id:\"auth0|me\""))
            .andExpect(decodedQueryParam("search_engine", "v3"))
            .andExpect(decodedQueryParam("fields", "user_id,name"))
            .andExpect(decodedQueryParam("page", "1"))
            .andExpect(decodedQueryParam("per_page", "5"))
            .andRespond(
                withSuccess(
                    """{"users":[{"user_id":"auth0|2","name":"Usario Dos"}],"total":6,"start":5,"limit":5}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val expected = Result.Success(UserPage(listOf(UserSummary("auth0|2", "Usario Dos")), 1, 5, 6))
        assertEquals(expected, directory.search("dos", "auth0|me", page = 1, size = 5))
        assertEquals(expected, directory.search("dos", "auth0|me", page = 1, size = 5))
    }

    @Test
    fun `a rejected token is forgotten so the next search asks for a new one`() {
        expectToken(ExpectedCount.twice())
        server
            .expect(ExpectedCount.twice(), requestTo(org.hamcrest.Matchers.startsWith("$BASE_URL/api/v2/users")))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        repeat(2) {
            val result = directory.search("", "auth0|me", page = 0, size = 10)
            assertTrue((result as Result.Failure).error is UserDirectoryUnavailable)
        }
    }

    @Test
    fun `Auth0 failures are reported as an unavailable directory`() {
        server
            .expect(requestTo("$BASE_URL/oauth/token"))
            .andRespond(withServerError())

        val result = directory.search("dos", "auth0|me", page = 0, size = 10)

        assertTrue((result as Result.Failure).error is UserDirectoryUnavailable)
    }

    private fun decodedQueryParam(
        name: String,
        expected: String,
    ): RequestMatcher =
        RequestMatcher { request ->
            val raw =
                UriComponentsBuilder
                    .fromUri(request.uri)
                    .build()
                    .queryParams
                    .getFirst(name)
            assertEquals(expected, raw?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }, "query parameter $name")
        }

    private companion object {
        const val BASE_URL = "https://tenant.auth0.test"
    }
}
