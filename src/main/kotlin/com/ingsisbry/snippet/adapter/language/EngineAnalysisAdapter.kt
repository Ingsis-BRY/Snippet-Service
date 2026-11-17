package com.ingsisbry.snippet.adapter.language

import com.ingsisbry.snippet.adapter.language.EngineRequests.Companion.LANGUAGE_HEADER
import com.ingsisbry.snippet.application.analysis.CodeIssue
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.port.out.language.FormatOutcome
import com.ingsisbry.snippet.application.port.out.language.LanguageCatalog
import com.ingsisbry.snippet.application.port.out.language.LanguageInfo
import com.ingsisbry.snippet.application.port.out.language.LintOutcome
import com.ingsisbry.snippet.application.port.out.language.RuleDefinition
import com.ingsisbry.snippet.application.port.out.language.RuleDefinitions
import com.ingsisbry.snippet.application.port.out.language.RuleType
import com.ingsisbry.snippet.application.port.out.language.SnippetFormatter
import com.ingsisbry.snippet.application.port.out.language.SnippetLinter
import com.ingsisbry.snippet.application.port.out.language.SnippetTestRunner
import com.ingsisbry.snippet.application.port.out.language.TestOutcome
import com.ingsisbry.snippet.application.port.out.language.TestSpec
import com.ingsisbry.snippet.domain.rules.RULES_LANGUAGE
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Everything else the engine does for Snippet: its languages and versions, the rule catalog, lint, format and
 * test runs. Problems in the code come back as [CodeIssue]s; only refused requests and outages are errors.
 */
@Component
class EngineAnalysisAdapter(
    @param:Qualifier("engineClient")
    private val engineClient: RestClient,
    @param:Qualifier("engineLongClient")
    private val engineLongClient: RestClient,
    private val languagesProperties: SnippetLanguagesProperties,
) : LanguageCatalog,
    RuleDefinitions,
    SnippetLinter,
    SnippetFormatter,
    SnippetTestRunner {
    private val requests = EngineRequests(LoggerFactory.getLogger(javaClass))

    override fun languages(): Result<SnippetError, List<LanguageInfo>> =
        languagesProperties.languages.collectAll { language -> language(language).map { listOf(it) } }

    private fun language(language: SnippetLanguagesProperties.LanguageProperties): Result<SnippetError, LanguageInfo> =
        requests
            .call("GET /versions", language.name, "") {
                engineClient
                    .get()
                    .uri("/versions")
                    .header(LANGUAGE_HEADER, language.name)
                    .retrieve()
                    .body(EngineVersionsResponse::class.java)
            }.flatMap { response ->
                if (response == null || response.versions.isEmpty()) {
                    requests.unavailable("the engine answered /versions without versions")
                } else {
                    Result.Success(
                        LanguageInfo(
                            language = language.name,
                            extensions = language.extensions,
                            versions = response.versions,
                            defaultVersion = response.default ?: response.versions.first(),
                            libraryVersion = response.libraryVersion,
                        ),
                    )
                }
            }

    override fun rules(kind: RuleKind): Result<SnippetError, List<RuleDefinition>> {
        val path = "/rules/${kind.name.lowercase()}"
        return requests
            .call("GET $path", RULES_LANGUAGE, "") {
                engineClient
                    .get()
                    .uri(path)
                    .header(LANGUAGE_HEADER, RULES_LANGUAGE)
                    .retrieve()
                    .body(RULE_LIST)
            }.flatMap { descriptors ->
                val rules = descriptors.orEmpty().map { it.toRuleDefinition() }
                if (rules.any { it == null }) {
                    requests.unavailable("the engine answered $path with an unknown rule type")
                } else {
                    Result.Success(rules.filterNotNull())
                }
            }
    }

    override fun lint(
        language: String,
        version: String,
        code: String,
        config: Map<String, Any>,
    ): Result<SnippetError, LintOutcome> =
        requests
            .call("POST /lint", language, version) {
                engineClient
                    .post()
                    .uri("/lint")
                    .header(LANGUAGE_HEADER, language)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EngineLintRequest(code = code, version = version, config = config))
                    .retrieve()
                    .body(EngineLintResponse::class.java)
            }.flatMap { response ->
                if (response == null) {
                    requests.unavailable("the engine answered /lint without a body")
                } else {
                    val issues =
                        response.violations.map { it.toCodeIssue() } + listOfNotNull(response.error?.toCodeIssue())
                    Result.Success(LintOutcome(valid = response.valid, issues = issues))
                }
            }

    override fun format(
        language: String,
        version: String,
        code: String,
        config: Map<String, Any>,
    ): Result<SnippetError, FormatOutcome> =
        requests
            .call("POST /format", language, version) {
                engineClient
                    .post()
                    .uri("/format")
                    .header(LANGUAGE_HEADER, language)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(EngineFormatRequest(code = code, version = version, config = config))
                    .retrieve()
                    .body(EngineFormatResponse::class.java)
            }.flatMap { response ->
                if (response == null || (response.formatted == null && response.error == null)) {
                    requests.unavailable("the engine answered /format without a result")
                } else {
                    Result.Success(
                        FormatOutcome(
                            formatted = response.formatted,
                            error = response.error?.toCodeIssue(),
                            ignoredRules = response.ignoredRules,
                        ),
                    )
                }
            }

    /** Sends at most [TESTS_PER_REQUEST] tests per request, so one request never waits on too many runs. */
    override fun run(
        language: String,
        version: String,
        code: String,
        tests: List<TestSpec>,
    ): Result<SnippetError, List<TestOutcome>> =
        tests.chunked(TESTS_PER_REQUEST).collectAll { chunk -> runChunk(language, version, code, chunk) }

    private fun runChunk(
        language: String,
        version: String,
        code: String,
        tests: List<TestSpec>,
    ): Result<SnippetError, List<TestOutcome>> =
        requests
            .call("POST /test", language, version) {
                engineLongClient
                    .post()
                    .uri("/test")
                    .header(LANGUAGE_HEADER, language)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        EngineTestRequest(
                            code = code,
                            version = version,
                            tests = tests.map { EngineTestCase(it.id, it.inputs, it.expectedOutputs) },
                        ),
                    ).retrieve()
                    .body(EngineTestResponse::class.java)
            }.flatMap { response ->
                val results = response?.results.orEmpty()
                if (results.map { it.id }.toSet() != tests.map { it.id }.toSet()) {
                    requests.unavailable("the engine answered /test without a result for every test")
                } else {
                    Result.Success(
                        results.map { TestOutcome(it.id, it.passed, it.actualOutputs, it.error?.toCodeIssue()) },
                    )
                }
            }

    private fun EngineRuleDescriptor.toRuleDefinition(): RuleDefinition? {
        val ruleType = RuleType.entries.firstOrNull { it.name.equals(type, ignoreCase = true) } ?: return null
        return RuleDefinition(
            key = key,
            type = ruleType,
            default = default,
            since = since,
            description = description,
            options = options,
            min = min,
            max = max,
        )
    }

    private companion object {
        const val TESTS_PER_REQUEST = 10
        val RULE_LIST = object : ParameterizedTypeReference<List<EngineRuleDescriptor>>() {}
    }
}

/** Calls [each] in order and joins the results; stops at the first failure. */
private fun <T, R> List<T>.collectAll(each: (T) -> Result<SnippetError, List<R>>): Result<SnippetError, List<R>> {
    val collected = mutableListOf<R>()
    for (item in this) {
        when (val result = each(item)) {
            is Result.Success -> collected += result.value
            is Result.Failure -> return result
        }
    }
    return Result.Success(collected)
}

internal fun EngineIssue.toCodeIssue(): CodeIssue = CodeIssue(rule, message, span?.toSourceSpan())
