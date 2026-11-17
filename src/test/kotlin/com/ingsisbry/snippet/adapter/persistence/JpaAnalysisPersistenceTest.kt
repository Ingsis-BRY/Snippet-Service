package com.ingsisbry.snippet.adapter.persistence

import com.ingsisbry.snippet.adapter.persistence.analysis.JpaSnippetAnalysisRepositoryAdapter
import com.ingsisbry.snippet.adapter.persistence.command.JpaSnippetRepositoryAdapter
import com.ingsisbry.snippet.adapter.persistence.query.JpaSnippetQueryRepositoryAdapter
import com.ingsisbry.snippet.adapter.persistence.query.filter.SnippetFilterRegistry
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.LanguageFilter
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.NameFilter
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.TextPredicateFactory
import com.ingsisbry.snippet.adapter.persistence.query.filter.parameters.ValidityFilter
import com.ingsisbry.snippet.adapter.persistence.rules.JpaUserRuleRepositoryAdapter
import com.ingsisbry.snippet.adapter.persistence.tests.JpaSnippetTestRepositoryAdapter
import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.analysis.FormatStatus
import com.ingsisbry.snippet.application.analysis.LintStatus
import com.ingsisbry.snippet.application.analysis.SnippetAnalysis
import com.ingsisbry.snippet.application.analysis.TestStatus
import com.ingsisbry.snippet.application.analysis.ValidationStatus
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SourcePosition
import com.ingsisbry.snippet.application.error.SourceSpan
import com.ingsisbry.snippet.application.error.TestNotFound
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.VersionRef
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.query.list.ListSnippetsQuery
import com.ingsisbry.snippet.application.query.list.SnippetSortField
import com.ingsisbry.snippet.application.query.list.SortDirection
import com.ingsisbry.snippet.application.query.tests.TestResult
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting
import com.ingsisbry.snippet.domain.test.SnippetTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * The JPA adapters against an in-memory database: the update queries, the stale-version query, the validity filter
 * and sort, the tests and the rules tables. Each adapter call commits on its own, as in production.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(
    JpaSnippetRepositoryAdapter::class,
    JpaSnippetAnalysisRepositoryAdapter::class,
    JpaSnippetTestRepositoryAdapter::class,
    JpaUserRuleRepositoryAdapter::class,
    JpaSnippetQueryRepositoryAdapter::class,
    SnippetFilterRegistry::class,
    NameFilter::class,
    LanguageFilter::class,
    ValidityFilter::class,
    TextPredicateFactory::class,
    JpaAnalysisPersistenceTest.Config::class,
)
class JpaAnalysisPersistenceTest {
    @Autowired
    private lateinit var snippets: JpaSnippetRepositoryAdapter

    @Autowired
    private lateinit var analysis: JpaSnippetAnalysisRepositoryAdapter

    @Autowired
    private lateinit var tests: JpaSnippetTestRepositoryAdapter

    @Autowired
    private lateinit var rules: JpaUserRuleRepositoryAdapter

    @Autowired
    private lateinit var queries: JpaSnippetQueryRepositoryAdapter

    @MockitoBean
    private lateinit var contentStorage: SnippetContentStorage

    private val owner = "auth0|${UUID.randomUUID()}"

    @Test
    fun `a new version is valid and waits for lint and format`() {
        val snippet = save()

        assertEquals(Result.Success(SnippetAnalysis.NEW), analysis.find(snippet.currentVersion.id))
        assertEquals(snippet.currentVersion.id, analysis.currentVersionId(snippet.id))
        assertNull(analysis.currentVersionId(UUID.randomUUID()))
    }

    @Test
    fun `results are saved per version, issues included`() {
        val versionId = save().currentVersion.id

        analysis.saveValidation(versionId, ValidationStatus.INVALID, ISSUE)
        analysis.saveLint(versionId, LintStatus.NOT_COMPLIANT, listOf(ISSUE, ISSUE.copy(span = null)))
        analysis.saveFormat(versionId, FormatStatus.FINISHED, "snippets/x/versions/y/formatted")

        assertEquals(
            Result.Success(
                SnippetAnalysis(
                    ValidationStatus.INVALID,
                    ISSUE,
                    LintStatus.NOT_COMPLIANT,
                    listOf(ISSUE, ISSUE.copy(span = null)),
                    FormatStatus.FINISHED,
                ),
            ),
            analysis.find(versionId),
        )
    }

    @Test
    fun `a failed format keeps the formatted blob already stored`() {
        val versionId = save().currentVersion.id
        analysis.saveFormat(versionId, FormatStatus.FINISHED, "the-key")

        analysis.saveFormat(versionId, FormatStatus.FAILED, null)

        assertEquals(setOf("the-key"), queries.findReferencedContentKeys(listOf("the-key")))
        assertEquals(FormatStatus.FAILED, (analysis.find(versionId) as Result.Success).value.formatStatus)
    }

    @Test
    fun `the formatted blob is served only while its format is finished`() {
        val snippet = save()
        val versionId = snippet.currentVersion.id
        Mockito.`when`(contentStorage.retrieve("snippets/${snippet.id}/original")).thenReturn(Result.Success("x"))
        Mockito.`when`(contentStorage.retrieve("old-rules")).thenReturn(Result.Success("formatted with old rules"))
        analysis.saveFormat(versionId, FormatStatus.FINISHED, "old-rules")

        assertEquals("formatted with old rules", formattedOf(snippet))

        analysis.markOwnerPending(owner, setOf(SnippetTask.FORMAT), NOW)
        assertNull(formattedOf(snippet))

        analysis.saveFormat(versionId, FormatStatus.FAILED, null)
        assertNull(formattedOf(snippet))
    }

    @Test
    fun `marking an owner pending touches only their current versions`() {
        val mine = save()
        val theirs = save(owner = "auth0|other-${UUID.randomUUID()}")
        listOf(mine, theirs).forEach { analysis.saveLint(it.currentVersion.id, LintStatus.COMPLIANT, emptyList()) }

        val refs = analysis.markOwnerPending(owner, setOf(SnippetTask.LINT), NOW)

        assertEquals(Result.Success(listOf(VersionRef(mine.id, mine.currentVersion.id))), refs)
        assertEquals(LintStatus.PENDING, lintOf(mine))
        assertEquals(LintStatus.COMPLIANT, lintOf(theirs))
    }

    @Test
    fun `stale versions are the pending ones requested long ago, with their pending tasks`() {
        val old = save(at = NOW.minusSeconds(3600))
        val recent = save(at = NOW)
        val done = save(at = NOW.minusSeconds(3600))
        analysis.saveLint(done.currentVersion.id, LintStatus.COMPLIANT, emptyList())
        analysis.saveFormat(done.currentVersion.id, FormatStatus.FINISHED, null)
        tests.save(test(done.id))

        val stale = analysis.findStale(NOW.minusSeconds(600), 1000).associateBy { it.ref.snippetId }

        assertEquals(setOf(SnippetTask.LINT, SnippetTask.FORMAT), stale.getValue(old.id).tasks)
        assertEquals(setOf(SnippetTask.TEST), stale.getValue(done.id).tasks)
        assertTrue(recent.id !in stale)
    }

    @Test
    fun `current versions page by snippet id`() {
        repeat(3) { save() }

        val all = analysis.currentVersions(null, 1000)
        val afterFirst = analysis.currentVersions(all.first().snippetId, 1000)

        assertEquals(all.drop(1), afterFirst)
        assertEquals(1, analysis.currentVersions(null, 1).size)
    }

    @Test
    fun `the list filters and sorts by the current version's compliance`() {
        val compliant = save(name = "b")
        val pending = save(name = "a")
        analysis.saveLint(compliant.currentVersion.id, LintStatus.COMPLIANT, emptyList())
        val ids = setOf(compliant.id, pending.id)

        val valid = queries.findAll(ListSnippetsQuery(owner, filters = mapOf("valid" to "true")), ids)
        val invalid = queries.findAll(ListSnippetsQuery(owner, filters = mapOf("valid" to "false")), ids)
        val byValidity = ListSnippetsQuery(owner, sortBy = SnippetSortField.VALID, direction = SortDirection.DESC)
        val sorted = queries.findAll(byValidity, ids)

        assertEquals(listOf(compliant.id), valid.content.map { it.id })
        assertEquals(LintStatus.COMPLIANT, valid.content.single().compliance)
        assertEquals(listOf(pending.id), invalid.content.map { it.id })
        assertEquals(listOf(pending.id, compliant.id), sorted.content.map { it.id })
    }

    @Test
    fun `a parser upgrade marks every snippet's tests pending too`() {
        val snippetId = save().id
        val test = test(snippetId)
        tests.save(test)
        tests.saveResult(test.id, TestResult(TestStatus.PASSED, listOf("out"), null, UUID.randomUUID(), NOW))

        analysis.markAllPending(setOf(SnippetTask.VALIDATE, SnippetTask.TEST), NOW)

        assertEquals(listOf(TestStatus.PENDING), tests.findAll(snippetId).map { it.status })
    }

    @Test
    fun `tests keep their order, their results and go back to pending`() {
        val snippetId = save().id
        val first = test(snippetId, createdAt = NOW)
        val second = test(snippetId, createdAt = NOW.plusSeconds(1))
        tests.save(second)
        tests.save(first)
        val versionId = UUID.randomUUID()

        tests.saveResult(first.id, TestResult(TestStatus.ERROR, listOf("1"), ISSUE, versionId, NOW))

        val listed = tests.findAll(snippetId)
        assertEquals(listOf(first, second), listed.map { it.test })
        assertEquals(TestStatus.ERROR, listed.first().status)
        assertEquals(listOf("1"), listed.first().actualOutputs)
        assertEquals(ISSUE, listed.first().error)
        assertEquals(2, tests.count(snippetId))

        tests.markPending(snippetId)
        assertEquals(listOf(TestStatus.PENDING, TestStatus.PENDING), tests.findAll(snippetId).map { it.status })
    }

    @Test
    fun `a test is found and deleted only through its snippet`() {
        val snippetId = save().id
        val test = test(snippetId)
        tests.save(test)
        val otherSnippet = UUID.randomUUID()

        assertNull(tests.find(otherSnippet, test.id))
        assertEquals(Result.Failure(TestNotFound(otherSnippet, test.id)), tests.delete(otherSnippet, test.id))
        assertEquals(Result.Success(Unit), tests.delete(snippetId, test.id))
        assertEquals(0, tests.count(snippetId))
    }

    @Test
    fun `deleting a snippet deletes its tests`() {
        val snippetId = save().id
        tests.save(test(snippetId))

        assertTrue(snippets.deleteById(snippetId) is Result.Success)

        assertEquals(0, tests.count(snippetId))
    }

    @Test
    fun `rules are replaced as a whole and keep their values' types`() {
        rules.replace(owner, RuleKind.FORMAT, listOf(RuleSetting("a", true, null), RuleSetting("b", true, 4)))

        rules.replace(
            owner,
            RuleKind.FORMAT,
            listOf(RuleSetting("b", false, 2), RuleSetting("c", true, "snake case")),
        )

        assertEquals(
            listOf(RuleSetting("b", false, 2), RuleSetting("c", true, "snake case")),
            rules.find(owner, RuleKind.FORMAT).sortedBy { it.key },
        )
        assertEquals(emptyList<RuleSetting>(), rules.find(owner, RuleKind.LINT))
    }

    private fun lintOf(snippet: Snippet): LintStatus {
        val found = analysis.find(snippet.currentVersion.id) as Result.Success
        return found.value.lintStatus
    }

    private fun formattedOf(snippet: Snippet): String? {
        val found = queries.findById(snippet.id)
        check(found is Result.Success)
        return found.value.currentVersion.formattedContent
    }

    private fun save(
        owner: String = this.owner,
        name: String = "s",
        at: Instant = NOW,
    ): Snippet {
        val id = UUID.randomUUID()
        val snippet = Snippet.create(id, name, "", "printscript", "1.1", owner, "x", UUID.randomUUID(), at)
        snippets.save(snippet, "snippets/$id/original")
        return snippet
    }

    private fun test(
        snippetId: UUID,
        createdAt: Instant = NOW,
    ) = SnippetTest(UUID.randomUUID(), snippetId, "t", listOf("in"), listOf("out"), createdAt)

    @TestConfiguration
    class Config {
        @Bean
        fun clock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-17T10:00:00Z")
        val ISSUE =
            CodeIssue("IdentifierFormat", "Use camel case", SourceSpan(SourcePosition(1, 5), SourcePosition(1, 7)))
    }
}
