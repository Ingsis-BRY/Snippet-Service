package com.ingsisbry.snippet.adapter.persistence.snippetversion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

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
)
