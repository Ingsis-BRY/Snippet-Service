package com.ingsisbry.snippet.adapter.persistence.snippet

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaSnippetRepository : JpaRepository<SnippetEntity, UUID>
