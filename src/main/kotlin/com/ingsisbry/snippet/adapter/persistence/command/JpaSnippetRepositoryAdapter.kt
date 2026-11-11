package com.ingsisbry.snippet.adapter.persistence.command

import com.ingsisbry.snippet.adapter.persistence.snippet.JpaSnippetRepository
import com.ingsisbry.snippet.adapter.persistence.snippet.SnippetEntity
import com.ingsisbry.snippet.adapter.persistence.snippetversion.JpaSnippetVersionRepository
import com.ingsisbry.snippet.adapter.persistence.snippetversion.SnippetVersionEntity
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.PersistenceUnavailable
import com.ingsisbry.snippet.application.error.SnippetConflict
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.error.SnippetNotFound
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.domain.Snippet
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Repository
class JpaSnippetRepositoryAdapter(
    private val repository: JpaSnippetRepository,
    private val versionRepository: JpaSnippetVersionRepository,
    private val transactionTemplate: TransactionTemplate,
) : SnippetRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun save(
        snippet: Snippet,
        originalContentKey: String,
    ): Result<SnippetError, Snippet> = persist(snippet, originalContentKey)

    override fun update(
        snippet: Snippet,
        originalContentKey: String,
    ): Result<SnippetError, Snippet> = persist(snippet, originalContentKey)

    override fun deleteById(id: UUID): Result<SnippetError, List<String>> = inTransaction(id) { deleteSnippet(id) }

    private fun persist(
        snippet: Snippet,
        originalContentKey: String,
    ): Result<SnippetError, Snippet> =
        inTransaction(snippet.id) {
            val version = snippet.currentVersion

            versionRepository.save(
                SnippetVersionEntity(
                    id = version.id,
                    snippetId = snippet.id,
                    versionNumber = version.versionNumber,
                    originalContentKey = originalContentKey,
                    formattedContentKey = null,
                    createdAt = version.createdAt,
                ),
            )

            repository.save(
                SnippetEntity(
                    id = snippet.id,
                    name = snippet.name,
                    description = snippet.description,
                    language = snippet.language,
                    languageVersion = snippet.languageVersion,
                    owner = snippet.owner,
                    currentVersionId = version.id,
                ),
            )

            Result.Success(snippet)
        }

    private fun deleteSnippet(id: UUID): Result<SnippetError, List<String>> {
        val snippet =
            repository.findById(id).orElse(null)
                ?: return Result.Failure(SnippetNotFound(id))

        val versions = versionRepository.findAllBySnippetId(id)

        val contentKeys =
            versions
                .flatMap { version ->
                    listOfNotNull(
                        version.originalContentKey,
                        version.formattedContentKey,
                    )
                }.distinct()

        versionRepository.deleteAll(versions)
        repository.delete(snippet)

        return Result.Success(contentKeys)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> inTransaction(
        snippetId: UUID,
        block: () -> Result<SnippetError, T>,
    ): Result<SnippetError, T> =
        try {
            transactionTemplate.execute { block() }
        } catch (exception: DataIntegrityViolationException) {
            logger.warn("Constraint violation while persisting snippet {}", snippetId, exception)
            Result.Failure(SnippetConflict(snippetId))
        } catch (exception: RuntimeException) {
            logger.error("Database operation failed for snippet {}", snippetId, exception)
            Result.Failure(PersistenceUnavailable(exception.message))
        }
}
