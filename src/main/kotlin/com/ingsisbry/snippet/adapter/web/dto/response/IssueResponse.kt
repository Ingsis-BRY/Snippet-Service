package com.ingsisbry.snippet.adapter.web.dto.response

import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan

/** `{rule, message, span: {start: {line, column}, end: {line, column}} | null}`, 1-based and inclusive. */
data class IssueResponse(
    val rule: String,
    val message: String,
    val span: SpanResponse?,
) {
    data class SpanResponse(
        val start: PositionResponse,
        val end: PositionResponse,
    )

    data class PositionResponse(
        val line: Int,
        val column: Int,
    )

    companion object {
        fun from(issue: CodeIssue): IssueResponse = IssueResponse(issue.rule, issue.message, issue.span?.let(::span))

        private fun span(span: SourceSpan): SpanResponse = SpanResponse(position(span.start), position(span.end))

        private fun position(position: SourcePosition): PositionResponse {
            val (line, column) = position
            return PositionResponse(line, column)
        }
    }
}
