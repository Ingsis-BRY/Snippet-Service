package com.ingsisbry.snippet.application.command.cleanup

import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.query.SnippetQueryRepository
import com.ingsisbry.snippet.application.port.out.storage.SnippetContentStorage
import com.ingsisbry.snippet.application.port.out.storage.StoredContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class DeleteOrphanedContentUseCase(
    private val contentStorage: SnippetContentStorage,
    private val snippetQueryRepository: SnippetQueryRepository,
    private val clock: Clock,
) : DeleteOrphanedContent {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(): Result<SnippetError, Int> {
        val cutoff = Instant.now(clock).minus(GRACE_PERIOD)

        return contentStorage.listAll().map { stored ->
            val candidates = stored.filter { it.lastModified.isBefore(cutoff) }
            findOrphans(candidates).count { key -> deleteQuietly(key) }
        }
    }

    private fun findOrphans(candidates: List<StoredContent>): List<String> {
        val keys = candidates.map { it.key }
        val referenced =
            keys
                .chunked(BATCH_SIZE)
                .flatMap { batch -> snippetQueryRepository.findReferencedContentKeys(batch) }
                .toSet()

        return keys.filterNot { it in referenced }
    }

    private fun deleteQuietly(key: String): Boolean =
        contentStorage
            .delete(key)
            .onFailure { error -> logger.warn("Failed to delete orphaned blob {}: {}", key, error) }
            .fold(onSuccess = { true }, onFailure = { false })

    companion object {
        val GRACE_PERIOD: Duration = Duration.ofHours(1)
        private const val BATCH_SIZE = 500
    }
}
