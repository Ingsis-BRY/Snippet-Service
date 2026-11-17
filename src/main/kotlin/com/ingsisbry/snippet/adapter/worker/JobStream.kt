package com.ingsisbry.snippet.adapter.worker

import java.time.Duration

/** One stream entry; [deliveries] is how many times the group has handed it out, this time included. */
data class StreamEntry(
    val id: String,
    val fields: Map<String, String>,
    val deliveries: Long = 1,
)

/**
 * The stream operations the consumer needs, for this worker's consumer in the workers' group. Any method may
 * throw when the stream is unreachable.
 */
interface JobStream {
    /** Creates the group at the start of the stream (creating the stream too); does nothing if it exists. */
    fun ensureGroup()

    /** Entries already delivered to this consumer and not acknowledged, after [afterId] (`0` = from the start). */
    fun readOwnPending(
        afterId: String,
        count: Int,
    ): List<StreamEntry>

    /** New entries, waiting up to [block] for one. */
    fun readNew(
        count: Int,
        block: Duration,
    ): List<StreamEntry>

    fun ack(id: String)

    /** Takes over entries of any consumer of the group that nobody acknowledged for at least [minIdle]. */
    fun claimIdle(
        minIdle: Duration,
        count: Int,
    ): List<StreamEntry>

    /** Copies an entry to the dead-letter stream with the reason it was given up. */
    fun deadLetter(
        fields: Map<String, String>,
        error: String,
    )
}
