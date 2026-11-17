package com.ingsisbry.snippet.adapter.persistence.rules

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface JpaUserRuleRepository : JpaRepository<UserRuleEntity, UUID> {
    fun findAllByUserIdAndKind(
        userId: String,
        kind: String,
    ): List<UserRuleEntity>

    @Modifying
    @Query("delete from UserRuleEntity r where r.userId = :userId and r.kind = :kind")
    fun deleteAllOf(
        @Param("userId") userId: String,
        @Param("kind") kind: String,
    ): Int
}
