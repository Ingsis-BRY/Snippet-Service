package com.ingsisbry.snippet.adapter.auth0

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class Auth0Configuration {
    @Bean
    fun auth0Client(
        // Spring's builder, instrumented with the observation registry (the calls show up in the trace)
        builder: RestClient.Builder,
        @Value("\${auth0.domain}")
        domain: String,
    ): RestClient =
        builder
            .clone()
            .baseUrl("https://$domain")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).build()

    @Bean
    fun managementApiTokens(
        auth0Client: RestClient,
        @Value("\${auth0.domain}")
        domain: String,
        @Value("\${auth0.management.client-id}")
        clientId: String,
        @Value("\${auth0.management.client-secret}")
        clientSecret: String,
    ): ManagementApiTokens =
        ManagementApiTokens(
            client = auth0Client,
            clientId = clientId,
            clientSecret = clientSecret,
            audience = "https://$domain/api/v2/",
        )

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
