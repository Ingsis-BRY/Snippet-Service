package com.ingsisbry.snippet.adapter.web

import com.newrelic.api.agent.NewRelic
import io.micrometer.tracing.Tracer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Returns the request's trace id in an `X-Trace-Id` response header, so a request seen in the browser's DevTools
 * can be looked up.
 *
 * When the New Relic agent is attached (every deployed environment) it is New Relic's trace id: the one its
 * Transactions, Spans, Distributed tracing and logs in context use, so pasting it in New Relic finds the request.
 * Without the agent (local runs, tests) it falls back to Micrometer's trace id, the `traceId` in the JSON logs.
 *
 * It runs right after Spring's observation filter, which opens the request's span, and before Spring Security, so
 * rejected requests (401, 403) carry it too. With neither the agent nor a tracer (web slice tests) it adds nothing.
 */
@Component
@Profile("!worker")
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class TraceIdResponseFilter internal constructor(
    private val tracer: ObjectProvider<Tracer>,
    private val agentTraceId: () -> String?,
) : OncePerRequestFilter() {
    @Autowired
    constructor(tracer: ObjectProvider<Tracer>) : this(tracer, ::newRelicTraceId)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        (agentTraceId()?.takeIf { it.isNotBlank() } ?: micrometerTraceId())
            ?.let { response.setHeader(HEADER, it) }
        filterChain.doFilter(request, response)
    }

    private fun micrometerTraceId(): String? =
        tracer.ifAvailable
            ?.currentSpan()
            ?.context()
            ?.traceId()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val HEADER = "X-Trace-Id"

        /** New Relic's trace id of the current transaction; empty without the agent (the API is then a no-op). */
        private fun newRelicTraceId(): String? = runCatching { NewRelic.getAgent().traceMetadata.traceId }.getOrNull()
    }
}
