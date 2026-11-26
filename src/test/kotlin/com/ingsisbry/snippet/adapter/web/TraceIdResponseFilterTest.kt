package com.ingsisbry.snippet.adapter.web

import io.micrometer.tracing.Tracer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TraceIdResponseFilterTest {
    private val noTracer = StaticListableBeanFactory().getBeanProvider(Tracer::class.java)

    private fun run(filter: TraceIdResponseFilter): Pair<MockHttpServletResponse, MockFilterChain> {
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(MockHttpServletRequest("POST", "/snippets"), response, chain)
        return response to chain
    }

    @Test
    fun `returns New Relic's trace id when the agent is attached`() {
        val (response, chain) = run(TraceIdResponseFilter(noTracer) { "a2dd0dee5d535210ba3331d99b8a7e7f" })

        assertEquals("a2dd0dee5d535210ba3331d99b8a7e7f", response.getHeader(TraceIdResponseFilter.HEADER))
        assertTrue(chain.request != null, "the request goes on down the chain")
    }

    @Test
    fun `adds nothing when there is neither an agent trace nor a tracer`() {
        val (response, chain) = run(TraceIdResponseFilter(noTracer) { "" })

        assertNull(response.getHeader(TraceIdResponseFilter.HEADER))
        assertTrue(chain.request != null, "the request goes on down the chain")
    }
}
