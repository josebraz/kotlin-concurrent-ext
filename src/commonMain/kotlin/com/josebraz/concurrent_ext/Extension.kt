package com.josebraz.concurrent_ext

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

inline fun Mutex.lockBlocking(owner: Any? = null) {
    while (!tryLock(owner));
}

internal inline fun <T> Mutex.withLockBlocking(owner: Any? = null, action: () -> T): T {
    try {
        lockBlocking(owner)
        return action()
    } finally {
        unlock(owner)
    }
}

inline fun measureTimeMillis(block: () -> Unit): Long {
    val after = Clock.System.now().toEpochMilliseconds()
    block()
    return Clock.System.now().toEpochMilliseconds() - after
}

suspend inline fun <T> suspendCoroutineWithTimeout(
    timeMillis: Long,
    crossinline block: (CancellableContinuation<T>) -> Unit
) = if (timeMillis > 0) {
    withTimeout(timeMillis) {
        suspendCancellableCoroutine(block = block)
    }
} else {
    suspendCancellableCoroutine(block = block)
}

public inline fun <T> MutableList<T>.removeFirstOrNull(predicate: (T) -> Boolean): T? {
    this.forEachIndexed { index, element ->
        if (predicate(element)) {
            return this.removeAt(index)
        }
    }
    return null
}