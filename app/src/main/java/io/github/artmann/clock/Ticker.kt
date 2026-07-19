package io.github.artmann.clock

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Emits Unit immediately and then every [periodMillis]. */
fun tickerFlow(periodMillis: Long = 1_000L): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMillis)
    }
}
