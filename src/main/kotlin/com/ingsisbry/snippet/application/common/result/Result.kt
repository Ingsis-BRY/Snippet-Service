package com.ingsisbry.snippet.application.common.result

sealed interface Result<out E, out T> {
    data class Success<out T>(
        val value: T,
    ) : Result<Nothing, T>

    data class Failure<out E>(
        val error: E,
    ) : Result<E, Nothing>

    fun <R> fold(
        onSuccess: (T) -> R,
        onFailure: (E) -> R,
    ): R =
        when (this) {
            is Success -> onSuccess(value)
            is Failure -> onFailure(error)
        }

    fun <R> map(transform: (T) -> R): Result<E, R> = flatMap { Success(transform(it)) }

    fun <R> flatMap(transform: (T) -> Result<@UnsafeVariance E, R>): Result<E, R> =
        fold(
            onSuccess = transform,
            onFailure = { Failure(it) },
        )

    fun <F> mapError(transform: (E) -> F): Result<F, T> =
        fold(
            onSuccess = { Success(it) },
            onFailure = { Failure(transform(it)) },
        )

    fun onSuccess(action: (T) -> Unit): Result<E, T> =
        also {
            if (it is Success) action(it.value)
        }

    fun onFailure(action: (E) -> Unit): Result<E, T> =
        also {
            if (it is Failure) action(it.error)
        }
}
