package com.ingsisbry.snippet.support

import com.ingsisbry.snippet.application.job.SnippetJob
import com.ingsisbry.snippet.application.port.out.job.SnippetJobPublisher

/** Records every job it is given; [failure] makes it throw, which a publisher must never do. */
class FakeSnippetJobPublisher(
    var failure: RuntimeException? = null,
) : SnippetJobPublisher {
    val published = mutableListOf<SnippetJob>()

    override fun publish(jobs: List<SnippetJob>) {
        failure?.let { throw it }
        published += jobs
    }
}
