package com.ingsisbry.snippet.application.command.tests

import java.util.UUID

/** A test's definition as the user writes it: ordered inputs and ordered expected `println` lines. */
data class TestDefinition(
    val name: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
)

data class CreateSnippetTestCommand(
    val snippetId: UUID,
    val userId: String,
    val definition: TestDefinition,
)

data class UpdateSnippetTestCommand(
    val snippetId: UUID,
    val testId: UUID,
    val userId: String,
    val definition: TestDefinition,
)
