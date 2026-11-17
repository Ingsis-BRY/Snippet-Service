package com.ingsisbry.snippet.application.port.out.job

import com.ingsisbry.snippet.application.job.SnippetJob

/** Best effort: never throws, never fails the caller. A lost job is recovered by the worker's sweeper. */
fun interface SnippetJobPublisher {
    fun publish(jobs: List<SnippetJob>)
}
