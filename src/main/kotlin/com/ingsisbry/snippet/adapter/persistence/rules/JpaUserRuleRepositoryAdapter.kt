package com.ingsisbry.snippet.adapter.persistence.rules

import com.ingsisbry.snippet.adapter.persistence.json.PersistenceJson
import com.ingsisbry.snippet.adapter.persistence.json.inResult
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.rules.UserRuleRepository
import com.ingsisbry.snippet.domain.rules.RuleKind
import com.ingsisbry.snippet.domain.rules.RuleSetting
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Repository
class JpaUserRuleRepositoryAdapter(
    private val repository: JpaUserRuleRepository,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : UserRuleRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun find(
        userId: String,
        kind: RuleKind,
    ): List<RuleSetting> =
        repository.findAllByUserIdAndKind(userId, kind.name).map { entity ->
            RuleSetting(
                key = entity.ruleKey,
                enabled = entity.enabled,
                value = PersistenceJson.readScalar(entity.valueJson),
            )
        }

    override fun replace(
        userId: String,
        kind: RuleKind,
        settings: List<RuleSetting>,
    ): Result<SnippetError, Unit> =
        transactionTemplate.inResult(logger, "replace the $kind rules of $userId") {
            repository.deleteAllOf(userId, kind.name)
            // the deletes must reach the database before the inserts, or the unique key would clash
            repository.flush()
            val now = Instant.now(clock)
            repository.saveAll(
                settings.map { setting ->
                    UserRuleEntity(
                        id = UUID.randomUUID(),
                        userId = userId,
                        kind = kind.name,
                        ruleKey = setting.key,
                        enabled = setting.enabled,
                        valueJson = PersistenceJson.writeScalar(setting.value),
                        updatedAt = now,
                    )
                },
            )
            Unit
        }
}
