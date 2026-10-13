package com.ingsisbry.snippet.adapter.persistence.command

import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

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
}
