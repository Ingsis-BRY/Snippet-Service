package com.ingsisbry.snippet.adapter.persistence.rules

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/** One user's setting for one rule; [valueJson] is a JSON scalar, null for boolean rules. */
@Entity
@Table(
    name = "user_rules",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_rules_user_kind_key", columnNames = ["user_id", "kind", "rule_key"]),
    ],
)
class UserRuleEntity(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false, length = 255)
    val userId: String,
    @Column(nullable = false, length = 16)
    val kind: String,
    @Column(name = "rule_key", nullable = false, length = 128)
    val ruleKey: String,
    @Column(nullable = false)
    val enabled: Boolean,
    @Column(name = "value_json", nullable = true, columnDefinition = "TEXT")
    val valueJson: String?,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
