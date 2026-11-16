package com.ingsisbry.snippet.adapter.permission

import com.ingsisbry.snippet.adapter.security.BearerTokenForwarder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PermissionServiceConfiguration {
    @Bean
    fun permissionServiceClient(
        @Value("\${permission-service.url}")
        baseUrl: String,
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestInterceptor(BearerTokenForwarder())
            // Fail fast instead of hanging a request thread when the permission service is down.
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT)
                    setReadTimeout(READ_TIMEOUT)
                },
            ).build()

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}
