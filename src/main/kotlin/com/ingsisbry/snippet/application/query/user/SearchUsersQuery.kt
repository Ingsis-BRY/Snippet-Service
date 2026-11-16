package com.ingsisbry.snippet.application.query.user

data class SearchUsersQuery(
    val requesterId: String,
    val name: String = "",
    val page: Int = 0,
    val size: Int = 10,
)
