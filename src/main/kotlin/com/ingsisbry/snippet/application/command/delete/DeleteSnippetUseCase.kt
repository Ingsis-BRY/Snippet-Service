package com.ingsisbry.snippet.application.command.delete

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.command.SnippetRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class DeleteSnippetUseCase(
    private val snippetRepository: SnippetRepository,
    private val contentStorage: SnippetContentStorage,
) : DeleteSnippet {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(id: UUID): Result<SnippetError, Unit> =
        snippetRepository.deleteById(id).map { contentKeys ->
            // The snippet is already gone from the database; a blob that cannot be deleted is only logged.
            contentKeys.forEach { key ->
                contentStorage.delete(key).onFailure { error ->
                    logger.error("Failed to delete snippet content blob {}: {}", key, error)
                }
            }
        }
}
