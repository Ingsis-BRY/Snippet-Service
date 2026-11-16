package com.ingsisbry.snippet.adapter.auth0

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.UserDirectoryUnavailable
import com.ingsisbry.snippet.application.port.out.user.UserDirectory
import com.ingsisbry.snippet.application.query.user.UserPage
import com.ingsisbry.snippet.application.query.user.UserSummary
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
class Auth0ManagementUserDirectory(
    @param:Qualifier("auth0Client")
    private val client: RestClient,
    private val tokens: ManagementApiTokens,
) : UserDirectory {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        size: Int,
    ): Result<SnippetError, UserPage> =
        try {
            val response =
                client
                    .get()
                    .uri { uri ->
                        uri
                            .path("/api/v2/users")
                            .queryParam("q", "{q}")
                            .queryParam("search_engine", "v3")
                            .queryParam("fields", "user_id,name")
                            .queryParam("include_fields", true)
                            .queryParam("include_totals", true)
                            .queryParam("page", page)
                            .queryParam("per_page", size)
                            .build(Auth0UserQuery.build(text, excludedUserId))
                    }.headers { it.setBearerAuth(tokens.current()) }
                    .retrieve()
                    .body(Auth0UserPage::class.java)
                    ?: Auth0UserPage()

            Result.Success(
                UserPage(
                    users = response.users.map { UserSummary(id = it.userId, name = it.name ?: it.userId) },
                    page = page,
                    size = size,
                    total = response.total,
                ),
            )
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.UNAUTHORIZED)) tokens.invalidate()
            logger.error("Auth0 user search answered {}", exception.statusCode)
            Result.Failure(UserDirectoryUnavailable("Auth0 answered ${exception.statusCode.value()}"))
        } catch (exception: RestClientException) {
            logger.error("Auth0 user search failed", exception)
            Result.Failure(UserDirectoryUnavailable(exception.message))
        } catch (exception: IllegalStateException) {
            logger.error("Auth0 user search failed", exception)
            Result.Failure(UserDirectoryUnavailable(exception.message))
        }
}
