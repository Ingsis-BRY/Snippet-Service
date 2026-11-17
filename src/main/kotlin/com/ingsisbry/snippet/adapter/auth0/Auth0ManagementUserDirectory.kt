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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class Auth0ManagementUserDirectory(
    @param:Qualifier("auth0Client")
    private val client: RestClient,
    private val tokens: ManagementApiTokens,
    private val clock: Clock = Clock.systemUTC(),
) : UserDirectory {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Display names by user id, kept for [NAME_TTL]: the list asks for the same owners on every page. */
    private val names = ConcurrentHashMap<String, CachedName>()

    override fun search(
        text: String,
        excludedUserId: String,
        page: Int,
        size: Int,
    ): Result<SnippetError, UserPage> =
        managementCall("search") {
            val response = queryUsers(Auth0UserQuery.build(text, excludedUserId), page, size, "user_id,name")
            UserPage(
                users = response.users.map { UserSummary(id = it.userId, name = it.name ?: it.userId) },
                page = page,
                size = size,
                total = response.total,
            )
        }

    override fun namesOf(ids: Set<String>): Result<SnippetError, Map<String, String>> {
        val now = Instant.now(clock)
        val missing = ids.filter { id -> names[id]?.takeIf { it.expiresAt.isAfter(now) } == null }
        return managementCall("name lookup") {
            missing.chunked(IDS_PER_REQUEST).forEach { batch ->
                val found = queryUsers(Auth0UserQuery.byIds(batch), 0, PAGE_SIZE, "user_id,name,email").users
                val expiresAt = now.plus(NAME_TTL)
                batch.forEach { id -> names[id] = CachedName(id, expiresAt) }
                found.forEach { user ->
                    names[user.userId] = CachedName(user.name ?: user.email ?: user.userId, expiresAt)
                }
            }
            ids.associateWith { id -> names[id]?.name ?: id }
        }
    }

    private fun queryUsers(
        query: String,
        page: Int,
        size: Int,
        fields: String,
    ): Auth0UserPage =
        client
            .get()
            .uri { uri ->
                uri
                    .path("/api/v2/users")
                    .queryParam("q", "{q}")
                    .queryParam("search_engine", "v3")
                    .queryParam("fields", fields)
                    .queryParam("include_fields", true)
                    .queryParam("include_totals", true)
                    .queryParam("page", page)
                    .queryParam("per_page", size)
                    .build(query)
            }.headers { it.setBearerAuth(tokens.current()) }
            .retrieve()
            .body(Auth0UserPage::class.java)
            ?: Auth0UserPage()

    private fun <T> managementCall(
        operation: String,
        call: () -> T,
    ): Result<SnippetError, T> =
        try {
            Result.Success(call())
        } catch (exception: RestClientResponseException) {
            if (exception.statusCode.isSameCodeAs(HttpStatus.UNAUTHORIZED)) tokens.invalidate()
            logger.error("Auth0 user {} answered {}", operation, exception.statusCode)
            Result.Failure(UserDirectoryUnavailable("Auth0 answered ${exception.statusCode.value()}"))
        } catch (exception: RestClientException) {
            logger.error("Auth0 user {} failed", operation, exception)
            Result.Failure(UserDirectoryUnavailable(exception.message))
        } catch (exception: IllegalStateException) {
            logger.error("Auth0 user {} failed", operation, exception)
            Result.Failure(UserDirectoryUnavailable(exception.message))
        }

    private data class CachedName(
        val name: String,
        val expiresAt: Instant,
    )

    private companion object {
        val NAME_TTL: Duration = Duration.ofMinutes(10)
        const val IDS_PER_REQUEST = 50
        const val PAGE_SIZE = 100
    }
}
