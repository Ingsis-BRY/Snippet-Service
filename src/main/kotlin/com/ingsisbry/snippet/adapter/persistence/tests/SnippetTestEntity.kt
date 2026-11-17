package com.ingsisbry.snippet.adapter.persistence.tests

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A test case of a snippet; inputs, outputs and the last error are JSON in TEXT columns. */
@Entity
@Table(
    name = "snippet_tests",
    indexes = [Index(name = "idx_snippet_tests_snippet_id", columnList = "snippet_id")],
)
@Suppress("LongParameterList")
class SnippetTestEntity(
    @Id
    val id: UUID,
    @Column(name = "snippet_id", nullable = false)
    val snippetId: UUID,
    @Column(nullable = false, length = 255)
    val name: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val inputs: String,
    @Column(name = "expected_outputs", nullable = false, columnDefinition = "TEXT")
    val expectedOutputs: String,
    @Column(nullable = false, length = 16)
    val status: String,
    @Column(name = "actual_outputs", nullable = true, columnDefinition = "TEXT")
    val actualOutputs: String?,
    @Column(nullable = true, columnDefinition = "TEXT")
    val error: String?,
    @Column(name = "last_run_version_id", nullable = true)
    val lastRunVersionId: UUID?,
    @Column(name = "last_run_at", nullable = true)
    val lastRunAt: Instant?,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
