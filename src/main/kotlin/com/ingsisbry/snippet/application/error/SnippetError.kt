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

/** The snippet content breaks a language rule; [span] is null when the engine has no position (e.g. NestingTooDeep). */
data class InvalidSnippetContent(
    val rule: String,
    val message: String,
    val span: SourceSpan?,
) : SnippetError

/** The language service could not complete the operation. */
data class LanguageServiceUnavailable(
    val reason: String?,
) : SnippetError

/** The user has no permission to perform the action on the snippet (or the snippet does not exist for them). */
data class AccessDenied(
    val snippetId: UUID,
    val userId: String,
) : SnippetError

/** The snippet is already shared with that user. */
data class AlreadyShared(
    val snippetId: UUID,
    val userId: String,
) : SnippetError

/** The permission service could not complete the operation. */
data class PermissionServiceUnavailable(
    val reason: String?,
) : SnippetError

/** The user directory (Auth0) could not complete the search. */
data class UserDirectoryUnavailable(
    val reason: String?,
) : SnippetError

/** The snippet has no test with that id. */
data class TestNotFound(
    val snippetId: UUID,
    val testId: UUID,
) : SnippetError

/** Transient errors are retried by the worker; others mark the job failed at once. */
fun SnippetError.isTransient(): Boolean =
    this is LanguageServiceUnavailable ||
        this is ContentStoreUnavailable ||
        this is PersistenceUnavailable ||
        this is PermissionServiceUnavailable
