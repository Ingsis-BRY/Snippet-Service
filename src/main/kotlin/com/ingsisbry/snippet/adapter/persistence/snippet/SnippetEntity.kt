package com.ingsisbry.snippet.adapter.persistence.snippet

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
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
)
