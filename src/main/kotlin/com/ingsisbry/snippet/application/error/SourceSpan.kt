package com.ingsisbry.snippet.application.error

/** A 1-based, inclusive position in a snippet's content. */
data class SourcePosition(
    val line: Int,
    val column: Int,
)

data class SourceSpan(
    val start: SourcePosition,
    val end: SourcePosition,
)
