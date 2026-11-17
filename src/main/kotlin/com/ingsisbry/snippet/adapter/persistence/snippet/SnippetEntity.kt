package com.ingsisbry.snippet.adapter.persistence.snippet

import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "snippets")
class SnippetEntity(
    @Id
    val id: UUID,
    @Column(nullable = false)
    val name: String,
    @Column(nullable = false)
    val description: String,
    @Column(nullable = false)
    val language: String,
    @Column(nullable = false)
    val languageVersion: String,
    @Column(nullable = false)
    val owner: String,
    @Column(name = "current_version_id", nullable = false)
    val currentVersionId: UUID,
    /**
     * Read-only view of [currentVersionId], only for filtering and sorting by the current version's statuses.
     * No foreign key: the schema stays as it was, and a snippet's versions can be deleted before the snippet.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "current_version_id",
        insertable = false,
        updatable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    val currentVersion: SnippetVersionEntity? = null,
)
