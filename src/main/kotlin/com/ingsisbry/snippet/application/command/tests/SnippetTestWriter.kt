package com.ingsisbry.snippet.application.command.tests

import com.ingsisbry.snippet.application.command.common.SnippetChangeNotifier
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.analysis.SnippetAnalysisRepository
import com.ingsisbry.snippet.application.port.out.tests.SnippetTestRepository
import com.ingsisbry.snippet.application.query.tests.SnippetTestView
import com.ingsisbry.snippet.domain.test.SnippetTest
import org.springframework.stereotype.Service

/** Saves a test definition (status PENDING) and asks the worker to run it against the current version. */
@Service
class SnippetTestWriter(
    private val repository: SnippetTestRepository,
    private val analysisRepository: SnippetAnalysisRepository,
    private val changes: SnippetChangeNotifier,
) {
    fun save(test: SnippetTest): Result<SnippetError, SnippetTestView> =
        repository.save(test).onSuccess {
            analysisRepository.currentVersionId(test.snippetId)?.let { versionId ->
                changes.testChanged(test.snippetId, versionId, test.id)
            }
        }
}
