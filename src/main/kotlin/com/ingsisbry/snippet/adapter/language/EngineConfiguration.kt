package com.ingsisbry.snippet.adapter.language

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
@EnableConfigurationProperties(SnippetLanguagesProperties::class)
class EngineConfiguration {
    @Bean("engineClient")
    fun engineClient(
        @Value("\${engine.url}")
        baseUrl: String,
    ): RestClient = engineClientBuilder(baseUrl).build()

    /** For `/test`: a request runs up to ten tests one after another, each with its own run timeout. */
    @Bean("engineLongClient")
    fun engineLongClient(
        @Value("\${engine.url}")
        baseUrl: String,
        @Value("\${engine.long-read-timeout:300s}")
        readTimeout: Duration,
    ): RestClient = engineClientBuilder(baseUrl, readTimeout).build()
}

private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
private val READ_TIMEOUT: Duration = Duration.ofSeconds(10)

/**
 * The engine proxy is internal and unauthenticated. This client deliberately has no `BearerTokenForwarder`,
 * so users' tokens never reach the engine. Fails fast instead of holding a request thread when it is down.
 */
internal fun engineClientBuilder(
    baseUrl: String,
    readTimeout: Duration = READ_TIMEOUT,
): RestClient.Builder =
    RestClient
        .builder()
        .baseUrl(baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(readTimeout)
            },
        )
