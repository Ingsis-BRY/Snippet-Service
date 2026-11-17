package com.ingsisbry.snippet.adapter.persistence.workerstate

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** A value the workers share, e.g. `engine.library-version.printscript`. Changed only by compare-and-set queries. */
@Entity
@Table(name = "worker_state")
class WorkerStateEntity(
    @Id
    @Column(name = "state_key", length = 128)
    val stateKey: String,
    @Column(name = "state_value", nullable = false, length = 255)
    val stateValue: String,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,
)
