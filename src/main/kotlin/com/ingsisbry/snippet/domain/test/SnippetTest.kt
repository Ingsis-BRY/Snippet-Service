package com.ingsisbry.snippet.domain.test

import java.time.Instant
import java.util.UUID

/** A test case of a snippet: the inputs it reads, in order, and the lines it must print, in order. */
data class SnippetTest(
    val id: UUID,
    val snippetId: UUID,
    val name: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
    val createdAt: Instant,
)
