package com.ingsisbry.snippet.adapter.persistence.snippetversion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One immutable version of a snippet's content, plus the results of analysing it. The statuses hold the names of
 * the application's status enums; they change only through `JpaSnippetVersionRepository`'s update queries.
 *
 * The NOT NULL status columns carry a database default because `ddl-auto: update` adds them to a table that
 * already has rows.
 */
@Entity
@Table(
    name = "snippet_versions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_snippet_versions_snippet_id_version_number",
            columnNames = ["snippet_id", "version_number"],
        ),
    ],
)
@Suppress("LongParameterList")
class SnippetVersionEntity(
    @Id
    val id: UUID,
    @Column(nullable = false)
    val snippetId: UUID,
    @Column(nullable = false)
    val versionNumber: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    val originalContentKey: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    val formattedContentKey: String?,
    @Column(nullable = false)
    val createdAt: Instant,
    @Column(name = "validation_status", columnDefinition = "varchar(16) default 'VALID' not null")
    val validationStatus: String = "VALID",
    @Column(name = "validation_error", nullable = true, columnDefinition = "TEXT")
    val validationError: String? = null,
    @Column(name = "lint_status", columnDefinition = "varchar(16) default 'PENDING' not null")
    val lintStatus: String = "PENDING",
    @Column(name = "lint_issues", nullable = true, columnDefinition = "TEXT")
    val lintIssues: String? = null,
    @Column(name = "format_status", columnDefinition = "varchar(16) default 'PENDING' not null")
    val formatStatus: String = "PENDING",
    @Column(name = "analysis_requested_at", nullable = true, columnDefinition = "timestamp(6) with time zone")
    val analysisRequestedAt: Instant? = null,
)
