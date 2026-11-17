package com.ingsisbry.snippet.adapter.web

import com.ingsisbry.snippet.adapter.security.CurrentUserId
import com.ingsisbry.snippet.adapter.web.dto.request.RuleUpdateRequest
import com.ingsisbry.snippet.adapter.web.dto.response.RuleResponse
import com.ingsisbry.snippet.adapter.web.mapper.SnippetErrorMapper
import com.ingsisbry.snippet.application.command.rules.RuleUpdate
import com.ingsisbry.snippet.application.command.rules.UpdateRules
import com.ingsisbry.snippet.application.command.rules.UpdateRulesCommand
import com.ingsisbry.snippet.application.common.result.Result
import com.ingsisbry.snippet.application.error.SnippetError
import com.ingsisbry.snippet.application.query.rules.GetRules
import com.ingsisbry.snippet.application.query.rules.RuleView
import com.ingsisbry.snippet.domain.rules.RuleKind
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The caller's format and lint rules (US#11, #14). Only these two kinds exist; anything else is a 404. */
@RestController
@Profile("!worker")
@RequestMapping("/rules")
class RuleController(
    private val getRules: GetRules,
    private val updateRules: UpdateRules,
    private val errorMapper: SnippetErrorMapper,
) {
    @GetMapping("/format")
    fun formatRules(
        @CurrentUserId userId: String,
    ): ResponseEntity<*> = getRules.execute(userId, RuleKind.FORMAT).respond()

    @GetMapping("/lint")
    fun lintRules(
        @CurrentUserId userId: String,
    ): ResponseEntity<*> = getRules.execute(userId, RuleKind.LINT).respond()

    @PutMapping("/format")
    fun updateFormatRules(
        @CurrentUserId userId: String,
        @RequestBody rules: List<RuleUpdateRequest>,
    ): ResponseEntity<*> = update(userId, RuleKind.FORMAT, rules)

    @PutMapping("/lint")
    fun updateLintRules(
        @CurrentUserId userId: String,
        @RequestBody rules: List<RuleUpdateRequest>,
    ): ResponseEntity<*> = update(userId, RuleKind.LINT, rules)

    private fun update(
        userId: String,
        kind: RuleKind,
        rules: List<RuleUpdateRequest>,
    ): ResponseEntity<*> =
        updateRules
            .execute(UpdateRulesCommand(userId, kind, rules.map { RuleUpdate(it.id, it.isActive, it.value) }))
            .respond()

    private fun Result<SnippetError, List<RuleView>>.respond(): ResponseEntity<*> =
        fold(
            onSuccess = { views -> ResponseEntity.ok(views.map(RuleResponse::from)) },
            onFailure = errorMapper::toResponse,
        )
}
