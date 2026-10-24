package it.traccia.core.common

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and wraps any exception into an [AppResult.Failure] mapped by [toError].
 * Coroutine cancellation is always rethrown so heavy operations stay cancellable.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> catching(
    toError: (Throwable) -> AppError = { AppError.Unexpected(it) },
    block: () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Failure(toError(e))
    }
