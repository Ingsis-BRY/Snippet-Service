package com.ingsisbry.snippet.application.analysis

/** What the background analysis knows about one snippet version. */
data class SnippetAnalysis(
    val validationStatus: ValidationStatus,
    val validationError: CodeIssue?,
    val lintStatus: LintStatus,
    val lintIssues: List<CodeIssue>,
    val formatStatus: FormatStatus,
) {
    /** Pending while any task is pending; failed if any failed; finished otherwise (invalid code is a result too). */
    fun processing(): ProcessingStatus =
        when {
            validationStatus == ValidationStatus.PENDING ||
                lintStatus == LintStatus.PENDING ||
                formatStatus == FormatStatus.PENDING -> ProcessingStatus.PENDING
            // invalid code can't be linted or formatted: that is its result, not a processing failure
            validationStatus == ValidationStatus.INVALID -> ProcessingStatus.FINISHED
            validationStatus == ValidationStatus.FAILED ||
                lintStatus == LintStatus.FAILED ||
                formatStatus == FormatStatus.FAILED -> ProcessingStatus.FAILED
            else -> ProcessingStatus.FINISHED
        }

    companion object {
        /** A version that was just validated and still waits for lint and format. */
        val NEW: SnippetAnalysis =
            SnippetAnalysis(
                validationStatus = ValidationStatus.VALID,
                validationError = null,
                lintStatus = LintStatus.PENDING,
                lintIssues = emptyList(),
                formatStatus = FormatStatus.PENDING,
            )
    }
}
