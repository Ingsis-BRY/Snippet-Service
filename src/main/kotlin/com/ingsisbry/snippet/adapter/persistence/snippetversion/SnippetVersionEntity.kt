package com.ingsisbry.snippet.adapter.persistence.snippetversion

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "snippet_versions")
class SnippetVersionEntity(
    @Id
    val id: UUID,
    @Column(nullable = false)
    val snippetId: UUID,
    @Column(nullable = false)
    val versionNumber: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    val originalContent: String,
    @Column(columnDefinition = "TEXT")
    val formattedContent: String?,
    @Column(nullable = false)
    val createdAt: Instant,
)
