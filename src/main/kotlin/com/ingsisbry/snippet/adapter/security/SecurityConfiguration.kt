package com.ingsisbry.snippet.adapter.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@Profile("!worker")
@EnableWebSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // before the token check: a CORS preflight (OPTIONS) carries no token and would get 401
            .cors(withDefaults())
            .authorizeHttpRequests {
                it
                    // the Swarm healthcheck and the probes call it without a token; it shows no details
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
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

    /**
     * CORS for a UI served from another origin (e.g. `npm run dev` on Vite's port 5173). The deployed UI calls
     * `/api` on its own origin through nginx and does not need it. Only the listed origins get CORS headers;
     * auth is a bearer token, never a cookie, so credentials are not allowed. CORS is enforced by the browser:
     * it does not authenticate the caller, the JWT does.
     */
    @Bean
    fun corsConfigurationSource(
        @Value("\${cors.allowed-origins}") allowedOrigins: List<String>,
    ): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                this.allowedOrigins = allowedOrigins.map { it.trim() }.filter { it.isNotEmpty() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "traceparent", "tracestate")
                // the download's file name and the trace id for support requests
                exposedHeaders = listOf("Content-Disposition", "X-Trace-Id")
                maxAge = PREFLIGHT_CACHE_SECONDS
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }

    private companion object {
        const val READ_SCOPE = "SCOPE_read:snippets"
        const val WRITE_SCOPE = "SCOPE_write:snippets"
        const val PREFLIGHT_CACHE_SECONDS = 86_400L
    }
}
