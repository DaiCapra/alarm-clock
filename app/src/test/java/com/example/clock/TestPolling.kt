package com.example.clock

import org.junit.Assert.fail

/** Real-time poll for work that completes on background threads (Room
 *  executors, unconfined coroutines) which virtual-time test dispatchers
 *  cannot await. */
fun awaitUntil(timeoutMs: Long = 5_000, message: String = "Condition not met", condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        if (System.currentTimeMillis() > deadline) fail("$message within ${timeoutMs}ms")
        Thread.sleep(10)
    }
}
