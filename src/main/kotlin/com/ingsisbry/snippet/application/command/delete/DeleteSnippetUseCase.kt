package com.ingsisbry.snippet.application.command.delete

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

    override fun execute(id: UUID): Boolean {
        val contentKeys =
            snippetRepository.deleteById(id)
                ?: return false

        contentKeys.forEach { key ->
            runCatching {
                contentStorage.delete(key)
            }.onFailure { exception ->
                logger.error(
                    "Failed to delete snippet content blob: {}",
                    key,
                    exception,
                )
            }
        }

        return true
    }
}
