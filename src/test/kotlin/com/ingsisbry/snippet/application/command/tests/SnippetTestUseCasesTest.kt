package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.command.common.SnippetChangeNotifier
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.AccessDenied
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.permission.SnippetAction
import com.ingsisbry.snippet.application.query.tests.ListSnippetTestsUseCase
import com.ingsisbry.snippet.domain.test.SnippetTest
import com.ingsisbry.snippet.support.FakeSnippetAnalysisRepository
import com.ingsisbry.snippet.support.FakeSnippetJobPublisher
import com.ingsisbry.snippet.support.FakeSnippetPermissions
import com.ingsisbry.snippet.support.FakeSnippetTestRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class SnippetTestUseCasesTest {
    private val now = Instant.parse("2026-11-17T10:00:00Z")
    private val snippetId = UUID.randomUUID()
    private val versionId = UUID.randomUUID()
    private val repository = FakeSnippetTestRepository()
    private val analysis = FakeSnippetAnalysisRepository().apply { addSnippet(snippetId, versionId, OWNER) }
    private val publisher = FakeSnippetJobPublisher()
    private val permissions = FakeSnippetPermissions()
    private val writer = SnippetTestWriter(repository, analysis, SnippetChangeNotifier(repository, publisher))

    private val create = CreateSnippetTestUseCase(repository, writer, permissions, Clock.fixed(now, ZoneOffset.UTC))
    private val update = UpdateSnippetTestUseCase(repository, writer, permissions)
    private val delete = DeleteSnippetTestUseCase(repository, permissions)
    private val list = ListSnippetTestsUseCase(repository, permissions)

    private val definition = TestDefinition("adds", listOf("1", "2"), listOf("3"))

    @Test
    fun `creating a test needs WRITE, stores it pending and runs it in the background`() {
        val result = create.execute(CreateSnippetTestCommand(snippetId, OWNER, definition))

        val view = (result as Result.Success).value
        assertEquals("adds", view.test.name)
        assertEquals(listOf("1", "2"), view.test.inputs)
        assertEquals(listOf("3"), view.test.expectedOutputs)
        assertEquals(now, view.test.createdAt)
        assertEquals(TestStatus.PENDING, view.status)
        assertEquals(SnippetAction.WRITE, permissions.checks.single().third)
        assertEquals(
            listOf(SnippetJob(snippetId, versionId, setOf(TEST), JobReason.TESTS_CHANGED, listOf(view.test.id))),
            publisher.published,
        )
    }

    @Test
    fun `a reader cannot create a test`() {
        permissions.allowed = false

        val result = create.execute(CreateSnippetTestCommand(snippetId, READER, definition))

        assertEquals(Result.Failure(AccessDenied(snippetId, READER)), result)
        assertTrue(repository.tests.isEmpty())
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `a snippet holds at most 50 tests`() {
        repeat(50) { repository.add(test("t$it")) }

        val result = create.execute(CreateSnippetTestCommand(snippetId, OWNER, definition))

        assertEquals(Result.Failure(InvalidInput("A snippet can have at most 50 tests")), result)
        assertEquals(50, repository.count(snippetId))
    }

    @Test
    fun `the definition's limits are checked before anything else`() {
        val cases =
            mapOf(
                TestDefinition(" ", emptyList(), emptyList()) to "name must not be blank",
                TestDefinition("x".repeat(256), emptyList(), emptyList()) to "name must be at most 255 characters",
                TestDefinition("n", List(101) { "i" }, emptyList()) to "input must have at most 100 items",
                TestDefinition("n", emptyList(), listOf("o".repeat(1001))) to
                    "each output item must be at most 1000 characters",
            )

        cases.forEach { (bad, reason) ->
            val result = create.execute(CreateSnippetTestCommand(snippetId, OWNER, bad))
            assertEquals(Result.Failure(InvalidInput(reason)), result)
        }
        assertTrue(permissions.checks.isEmpty())
    }

    @Test
    fun `updating a test keeps its id and creation time and runs it again`() {
        val existing = repository.add(test("old"), status = TestStatus.PASSED).test

        val result = update.execute(UpdateSnippetTestCommand(snippetId, existing.id, OWNER, definition))

        val view = (result as Result.Success).value
        assertEquals(existing.id, view.test.id)
        assertEquals(existing.createdAt, view.test.createdAt)
        assertEquals("adds", view.test.name)
        assertEquals(TestStatus.PENDING, view.status)
        assertEquals(listOf(existing.id), publisher.published.single().testIds)
    }

    @Test
    fun `updating or deleting a test of another snippet is TestNotFound`() {
        val other = repository.add(test("other").copy(snippetId = UUID.randomUUID())).test

        assertEquals(
            Result.Failure(TestNotFound(snippetId, other.id)),
            update.execute(UpdateSnippetTestCommand(snippetId, other.id, OWNER, definition)),
        )
        assertEquals(Result.Failure(TestNotFound(snippetId, other.id)), delete.execute(snippetId, other.id, OWNER))
        assertTrue(publisher.published.isEmpty())
    }

    @Test
    fun `deleting needs WRITE`() {
        val existing = repository.add(test("t")).test
        permissions.allowed = false

        assertEquals(Result.Failure(AccessDenied(snippetId, READER)), delete.execute(snippetId, existing.id, READER))
        assertEquals(1, repository.count(snippetId))
    }

    @Test
    fun `the owner deletes a test`() {
        val existing = repository.add(test("t")).test

        assertEquals(Result.Success(Unit), delete.execute(snippetId, existing.id, OWNER))
        assertEquals(0, repository.count(snippetId))
    }

    @Test
    fun `anyone with READ lists the tests in order`() {
        val first = repository.add(test("first")).test
        val second = repository.add(test("second")).test

        val result = list.execute(snippetId, READER)

        assertEquals(listOf(first, second), (result as Result.Success).value.map { it.test })
        assertEquals(SnippetAction.READ, permissions.checks.single().third)
    }

    @Test
    fun `without READ the tests are not listed`() {
        permissions.allowed = false

        assertEquals(Result.Failure(AccessDenied(snippetId, "intruder")), list.execute(snippetId, "intruder"))
    }

    private fun test(name: String) = SnippetTest(UUID.randomUUID(), snippetId, name, emptyList(), emptyList(), now)

    private companion object {
        const val OWNER = "auth0|owner"
        const val READER = "auth0|reader"
        val TEST = SnippetTask.TEST
    }
}
