package com.ingsisbry.snippet.application.analysis

enum class ValidationStatus { PENDING, VALID, INVALID, FAILED }

/** Compliance: whether the snippet passes its owner's lint rules. */
enum class LintStatus { PENDING, COMPLIANT, NOT_COMPLIANT, FAILED }

enum class FormatStatus { PENDING, FINISHED, FAILED }

enum class TestStatus { PENDING, PASSED, FAILED, ERROR }

/** Derived from the other statuses, never stored. */
enum class ProcessingStatus { PENDING, FINISHED, FAILED }
