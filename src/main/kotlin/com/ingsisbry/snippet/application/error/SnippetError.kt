package com.ingsisbry.snippet.application.error

import java.util.UUID

sealed interface SnippetError

data class SnippetNotFound(
    val id: UUID,
) : SnippetError

/** The snippet metadata could not be saved because it conflicts with existing data. */
data class SnippetConflict(
    val id: UUID,
) : SnippetError

/** The content store (blob storage) could not complete the operation. */
data class ContentStoreUnavailable(
    val key: String,
    val reason: String?,
) : SnippetError

/** The snippet references content that does not exist in the content store. */
data class ContentMissing(
    val key: String,
) : SnippetError

/** The database could not complete the operation. */
data class PersistenceUnavailable(
    val reason: String?,
) : SnippetError

/** Stored snippet data is internally inconsistent (e.g. a snippet pointing to a missing version). */
data class SnippetCorrupted(
    val id: UUID,
    val reason: String,
) : SnippetError

data class InvalidInput(
    val reason: String,
) : SnippetError

/** The language, or that version of it, is not supported by the language service. */
data class UnsupportedLanguage(
    val language: String,
    val languageVersion: String,
) : SnippetError

/** The snippet content breaks a language rule at the given position. */
data class InvalidSnippetContent(
    val rule: String,
    val line: Int,
    val column: Int,
) : SnippetError

/** The language service could not complete the operation. */
data class LanguageServiceUnavailable(
    val reason: String?,
) : SnippetError
