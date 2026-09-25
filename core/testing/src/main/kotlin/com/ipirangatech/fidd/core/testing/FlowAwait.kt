package com.ipirangatech.fidd.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Waits in *real* time for the first value matching [predicate]. Needed inside `runTest` when the
 * value is produced off the test scheduler (DataStore file I/O, PotholeDetector's
 * Dispatchers.Default scope): plain `withTimeout` there uses virtual time and gives up instantly,
 * and `runBlocking` would block the scheduler the result depends on.
 */
suspend fun <T> Flow<T>.awaitFirst(
    timeout: Duration = 3.seconds,
    predicate: (T) -> Boolean,
): T = withContext(Dispatchers.Default) { withTimeout(timeout) { first(predicate) } }

/** Real-time wait for [condition] to become true, polling every 10 ms. */
suspend fun awaitCondition(
    timeout: Duration = 3.seconds,
    condition: () -> Boolean,
) = withContext(Dispatchers.Default) {
    withTimeout(timeout) { while (!condition()) kotlinx.coroutines.delay(10) }
}
