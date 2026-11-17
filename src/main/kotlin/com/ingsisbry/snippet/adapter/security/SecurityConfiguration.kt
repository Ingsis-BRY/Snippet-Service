package com.ingsisbry.snippet.adapter.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.GET, "/**")
                    .hasAuthority(READ_SCOPE)
                    .anyRequest()
                    .hasAuthority(WRITE_SCOPE)
            }.oauth2ResourceServer {
                // the run socket's handshake carries the token in Sec-WebSocket-Protocol; it is a GET, so the
                // read scope applies to it like to any other read
                it.bearerTokenResolver(WebSocketBearerTokenResolver())
                it.jwt(withDefaults())
            }.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .csrf { it.disable() }
        return http.build()
    }

    private companion object {
        const val READ_SCOPE = "SCOPE_read:snippets"
        const val WRITE_SCOPE = "SCOPE_write:snippets"
    }
}
