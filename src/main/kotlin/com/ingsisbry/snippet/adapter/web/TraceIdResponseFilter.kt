package com.ingsisbry.snippet.adapter.web

import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Returns the request's trace id in an `X-Trace-Id` response header, so a request seen in the browser's DevTools
 * can be found in the JSON logs (`traceId`) and in New Relic. It runs right after Spring's observation filter,
 * which opens the request's span, and before Spring Security, so rejected requests (401, 403) carry it too.
 * Without a tracer (web slice tests) it adds nothing.
 */
@Component
@Profile("!worker")
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TraceIdResponseFilter(
    private val tracer: ObjectProvider<Tracer>,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        tracer.ifAvailable
            ?.currentSpan()
            ?.context()
            ?.traceId()
            ?.takeIf { it.isNotBlank() }
            ?.let { response.setHeader(HEADER, it) }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-Trace-Id"
    }
}
