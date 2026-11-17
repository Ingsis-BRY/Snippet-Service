package com.ingsisbry.snippet.adapter.auth0

import com.fasterxml.jackson.annotation.JsonProperty

internal data class ClientCredentialsRequest(
    @param:JsonProperty("client_id")
    @get:JsonProperty("client_id")
    val clientId: String,
    @param:JsonProperty("client_secret")
    @get:JsonProperty("client_secret")
    val clientSecret: String,
    val audience: String,
    @param:JsonProperty("grant_type")
    @get:JsonProperty("grant_type")
    val grantType: String = "client_credentials",
)

internal data class TokenResponse(
    @param:JsonProperty("access_token")
    val accessToken: String,
    @param:JsonProperty("expires_in")
    val expiresIn: Long,
)

internal data class Auth0User(
    @param:JsonProperty("user_id")
    val userId: String,
    val name: String?,
    val email: String? = null,
)

internal data class Auth0UserPage(
    val users: List<Auth0User> = emptyList(),
    val total: Long = 0,
)
