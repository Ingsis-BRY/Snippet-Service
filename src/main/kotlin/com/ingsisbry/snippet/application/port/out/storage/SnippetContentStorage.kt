package com.ingsisbry.snippet.application.port.out.storage

interface SnippetContentStorage {
    fun store(
        key: String,
        content: String,
    )

    fun retrieve(key: String): String

    fun delete(key: String)
}
