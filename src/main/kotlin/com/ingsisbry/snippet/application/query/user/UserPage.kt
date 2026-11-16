package com.ingsisbry.snippet.application.query.user

data class UserSummary(
    val id: String,
    val name: String,
)

data class UserPage(
    val users: List<UserSummary>,
    val page: Int,
    val size: Int,
    val total: Long,
)
