package com.ingsisbry.snippet.application.command.rules

import com.ingsisbry.snippet.application.command.common.SnippetChangeNotifier
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.InvalidInput
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.job.JobReason
import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.job.SnippetTask
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.rules.UserRuleRepository
import com.ingsisbry.snippet.application.query.rules.ResolveRuleConfigUseCase
import com.ingsisbry.snippet.application.query.rules.RuleView
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * Validates the rules against the engine's catalog, lets the engine check the resulting config, stores it and
 * marks the owner's snippets pending. It never waits for the reprocessing, which runs in the background.
 */
@Service
class UpdateRulesUseCase(
    private val definitions: RuleDefinitions,
    private val configCheck: RuleConfigCheck,
    private val repository: UserRuleRepository,
    private val analysisRepository: SnippetAnalysisRepository,
    private val changes: SnippetChangeNotifier,
    private val clock: Clock,
) : UpdateRules {
    override fun execute(command: UpdateRulesCommand): Result<SnippetError, List<RuleView>> =
        definitions.rules(command.kind).flatMap { catalog ->
            val validated = RuleSettingsValidator.validate(catalog, command.rules)
            if (validated.violation != null) {
                Result.Failure(InvalidInput(validated.violation))
            } else {
                configCheck
                    .check(command.kind, ResolveRuleConfigUseCase.toConfig(validated.settings))
                    .flatMap { repository.replace(command.userId, command.kind, validated.settings) }
                    .flatMap { reprocess(command.userId, command.kind) }
                    .map { RuleView.of(catalog, validated.settings) }
            }
        }

    /**
     * Marks the owner's current versions pending and publishes one job each. If marking fails the request fails
     * (a retry of the same PUT is harmless); a lost job is recovered by the worker's sweeper.
     */
    private fun reprocess(
        userId: String,
        kind: RuleKind,
    ): Result<SnippetError, Unit> {
        val (task, reason) =
            when (kind) {
                RuleKind.LINT -> SnippetTask.LINT to JobReason.LINT_RULES_CHANGED
                RuleKind.FORMAT -> SnippetTask.FORMAT to JobReason.FORMAT_RULES_CHANGED
            }
        return analysisRepository
            .markOwnerPending(userId, setOf(task), Instant.now(clock))
            .map { refs -> changes.publish(refs.map { SnippetJob(it.snippetId, it.versionId, setOf(task), reason) }) }
    }
}
