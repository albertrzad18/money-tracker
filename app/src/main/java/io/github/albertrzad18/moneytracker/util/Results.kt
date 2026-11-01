package io.github.albertrzad18.moneytracker.util

import kotlin.coroutines.cancellation.CancellationException

/** Like [runCatching], but lets coroutine cancellation through. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
