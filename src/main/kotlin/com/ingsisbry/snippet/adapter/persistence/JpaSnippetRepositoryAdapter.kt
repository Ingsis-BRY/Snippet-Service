package com.ingsisbry.snippet.adapter.persistence

import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import com.ingsisbry.snippet.domain.SnippetVersion
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class JpaSnippetRepositoryAdapter(
    private val repository: JpaSnippetRepository,
    private val versionRepository: JpaSnippetVersionRepository,
) : SnippetRepository {
    @Transactional
    override fun save(snippet: Snippet): Snippet {
        val versionEntity =
            SnippetVersionEntity(
                id = snippet.currentVersion.id,
                snippetId = snippet.currentVersion.snippetId,
                versionNumber = snippet.currentVersion.versionNumber,
                originalContent = snippet.currentVersion.originalContent,
                formattedContent = snippet.currentVersion.formattedContent,
                createdAt = snippet.currentVersion.createdAt,
            )

        versionRepository.save(versionEntity)

        val snippetEntity =
            SnippetEntity(
                id = snippet.id,
                name = snippet.name,
                description = snippet.description,
                language = snippet.language,
                languageVersion = snippet.languageVersion,
                owner = snippet.owner,
                currentVersionId = snippet.currentVersion.id,
            )

        repository.save(snippetEntity)

        return snippet
    }

    @Transactional
    override fun findById(id: UUID): Snippet? {
        val entity = repository.findById(id).orElse(null) ?: return null

        val versionEntity =
            versionRepository
                .findById(entity.currentVersionId)
                .orElseThrow {
                    IllegalStateException(
                        "Snippet ${entity.id} references missing version ${entity.currentVersionId}",
                    )
                }

        return Snippet(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            language = entity.language,
            languageVersion = entity.languageVersion,
            owner = entity.owner,
            currentVersion =
                SnippetVersion(
                    id = versionEntity.id,
                    snippetId = versionEntity.snippetId,
                    versionNumber = versionEntity.versionNumber,
                    originalContent = versionEntity.originalContent,
                    formattedContent = versionEntity.formattedContent,
                    createdAt = versionEntity.createdAt,
                ),
        )
    }
}
