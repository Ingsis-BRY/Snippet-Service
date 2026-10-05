package com.ingsisbry.snippet.adapter.persistence.snippetversion

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaSnippetVersionRepository : JpaRepository<SnippetVersionEntity, UUID>
