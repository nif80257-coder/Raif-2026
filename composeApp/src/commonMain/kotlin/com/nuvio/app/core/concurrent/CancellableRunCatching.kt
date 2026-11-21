package com.nuvio.app.core.concurrent

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] that lets cancellation through.
 *
 * The standard `runCatching` catches every `Throwable`, `CancellationException` included, so work
 * that was cancelled reports itself as a failure: a stale error message, a retry that never ends,
 * or a state write after the screen that asked for it is already gone. A cancelled call is not a
 * failure, so it is rethrown here and only a real error becomes a `Result.failure`.
 *
 * A block that cannot suspend keeps exactly the behaviour of `runCatching`; use this one wherever
 * the block may reach a suspension point.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
