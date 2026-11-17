package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.CurrentUserId
import com.ingsisbry.snippet.adapter.web.dto.request.TestCaseRequest
import com.ingsisbry.snippet.adapter.web.dto.response.TestCaseResponse
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.tests.CreateSnippetTest
import com.ingsisbry.snippet.application.command.tests.CreateSnippetTestCommand
import com.ingsisbry.snippet.application.command.tests.DeleteSnippetTest
import com.ingsisbry.snippet.application.command.tests.TestDefinition
import com.ingsisbry.snippet.application.command.tests.UpdateSnippetTest
import com.ingsisbry.snippet.application.command.tests.UpdateSnippetTestCommand
import com.ingsisbry.snippet.application.query.tests.ListSnippetTests
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** A snippet's test cases (US#8): the owner writes them, anyone who can read the snippet lists them. */
@RestController
@Profile("!worker")
@RequestMapping("/snippets/{snippetId}/tests")
class SnippetTestController(
    private val listSnippetTests: ListSnippetTests,
    private val createSnippetTest: CreateSnippetTest,
    private val updateSnippetTest: UpdateSnippetTest,
    private val deleteSnippetTest: DeleteSnippetTest,
    private val errorMapper: SnippetErrorMapper,
) {
    @GetMapping
    fun list(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
    ): ResponseEntity<*> =
        listSnippetTests
            .execute(snippetId, userId)
            .fold(
                onSuccess = { tests -> ResponseEntity.ok(tests.map(TestCaseResponse::from)) },
                onFailure = errorMapper::toResponse,
            )

    @PostMapping
    fun create(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @RequestBody request: TestCaseRequest,
    ): ResponseEntity<*> =
        createSnippetTest
            .execute(CreateSnippetTestCommand(snippetId, userId, request.toDefinition()))
            .fold(
                onSuccess = { test -> ResponseEntity.status(HttpStatus.CREATED).body(TestCaseResponse.from(test)) },
                onFailure = errorMapper::toResponse,
            )

    @PutMapping("/{testId}")
    fun update(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @PathVariable testId: UUID,
        @RequestBody request: TestCaseRequest,
    ): ResponseEntity<*> =
        updateSnippetTest
            .execute(UpdateSnippetTestCommand(snippetId, testId, userId, request.toDefinition()))
            .fold(
                onSuccess = { test -> ResponseEntity.ok(TestCaseResponse.from(test)) },
                onFailure = errorMapper::toResponse,
            )

    @DeleteMapping("/{testId}")
    fun delete(
        @CurrentUserId userId: String,
        @PathVariable snippetId: UUID,
        @PathVariable testId: UUID,
    ): ResponseEntity<*> =
        deleteSnippetTest
            .execute(snippetId, testId, userId)
            .fold(
                onSuccess = { ResponseEntity.noContent().build<Void>() },
                onFailure = errorMapper::toResponse,
            )

    private fun TestCaseRequest.toDefinition(): TestDefinition =
        TestDefinition(name = name, inputs = input.orEmpty(), expectedOutputs = output.orEmpty())
}
