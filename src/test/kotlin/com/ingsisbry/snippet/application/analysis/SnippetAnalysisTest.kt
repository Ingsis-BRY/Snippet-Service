package com.ingsisbry.snippet.application.analysis

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnippetAnalysisTest {
    private val finished =
        SnippetAnalysis(ValidationStatus.VALID, null, LintStatus.COMPLIANT, emptyList(), FormatStatus.FINISHED)

    @Test
    fun `a new version is pending`() {
        assertEquals(ProcessingStatus.PENDING, SnippetAnalysis.NEW.processing())
    }

    @Test
    fun `everything done is finished, including invalid or non-compliant code`() {
        assertEquals(ProcessingStatus.FINISHED, finished.processing())
        assertEquals(
            ProcessingStatus.FINISHED,
            finished
                .copy(validationStatus = ValidationStatus.INVALID, lintStatus = LintStatus.NOT_COMPLIANT)
                .processing(),
        )
    }

    @Test
    fun `invalid code is finished even though it could not be formatted`() {
        val invalid =
            finished.copy(
                validationStatus = ValidationStatus.INVALID,
                lintStatus = LintStatus.NOT_COMPLIANT,
                formatStatus = FormatStatus.FAILED,
            )

        assertEquals(ProcessingStatus.FINISHED, invalid.processing())
    }

    @Test
    fun `any pending task makes it pending, even next to a failure`() {
        assertEquals(ProcessingStatus.PENDING, finished.copy(validationStatus = ValidationStatus.PENDING).processing())
        assertEquals(ProcessingStatus.PENDING, finished.copy(lintStatus = LintStatus.PENDING).processing())
        assertEquals(
            ProcessingStatus.PENDING,
            finished.copy(formatStatus = FormatStatus.PENDING, lintStatus = LintStatus.FAILED).processing(),
        )
    }

    @Test
    fun `a failed task without pending ones is failed`() {
        assertEquals(ProcessingStatus.FAILED, finished.copy(validationStatus = ValidationStatus.FAILED).processing())
        assertEquals(ProcessingStatus.FAILED, finished.copy(lintStatus = LintStatus.FAILED).processing())
        assertEquals(ProcessingStatus.FAILED, finished.copy(formatStatus = FormatStatus.FAILED).processing())
    }
}
