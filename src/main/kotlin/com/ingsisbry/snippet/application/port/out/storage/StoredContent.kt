package com.ingsisbry.snippet.application.port.out.storage

import java.time.Instant

data class StoredContent(
    val key: String,
    val lastModified: Instant,
)
