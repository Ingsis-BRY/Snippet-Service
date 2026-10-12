package com.ingsisbry.snippet.application.common.pagination

data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> empty(
            page: Int,
            size: Int,
        ): PageResult<T> =
            PageResult(
                content = emptyList(),
                page = page,
                size = size,
                totalElements = 0,
                totalPages = 0,
            )
    }
}
