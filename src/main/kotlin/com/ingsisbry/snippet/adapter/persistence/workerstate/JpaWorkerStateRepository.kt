package com.ingsisbry.snippet.adapter.persistence.workerstate

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JpaWorkerStateRepository : JpaRepository<WorkerStateEntity, String> {
    @Modifying
    @Query(
        "update WorkerStateEntity s set s.stateValue = :new, s.updatedAt = :at " +
            "where s.stateKey = :key and s.stateValue = :expected",
    )
    fun replaceIf(
        @Param("key") key: String,
        @Param("expected") expected: String,
        @Param("new") new: String,
        @Param("at") at: Instant,
    ): Int

    /** A plain insert, never a merge: two workers inserting the same key must clash, not overwrite each other. */
    @Modifying
    @Query(
        value = "insert into worker_state (state_key, state_value, updated_at) values (:key, :value, :at)",
        nativeQuery = true,
    )
    fun insert(
        @Param("key") key: String,
        @Param("value") value: String,
        @Param("at") at: Instant,
    ): Int
}
