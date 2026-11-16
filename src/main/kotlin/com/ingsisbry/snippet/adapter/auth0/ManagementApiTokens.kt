package com.ingsisbry.snippet.adapter.auth0

import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ManagementApiTokens(
    private val client: RestClient,
    private val clientId: String,
    private val clientSecret: String,
    private val audience: String,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var cached: CachedToken? = null

    @Synchronized
    fun current(): String {
        val now = Instant.now(clock)
        val token = cached?.takeIf { it.expiresAt.isAfter(now.plus(EXPIRY_MARGIN)) } ?: request(now)
        cached = token
        return token.value
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    private fun request(now: Instant): CachedToken {
        val response =
            client
                .post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    ClientCredentialsRequest(
                        clientId = clientId,
                        clientSecret = clientSecret,
                        audience = audience,
                    ),
                ).retrieve()
                .body(TokenResponse::class.java)
                ?: error("Auth0 returned an empty token response")

        return CachedToken(response.accessToken, now.plusSeconds(response.expiresIn))
    }

    private data class CachedToken(
        val value: String,
        val expiresAt: Instant,
    )

    private companion object {
        val EXPIRY_MARGIN: Duration = Duration.ofMinutes(1)
    }
}
