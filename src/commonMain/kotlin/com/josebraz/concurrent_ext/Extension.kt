package com.josebraz.concurrent_ext

import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock

inline fun Mutex.lockBlocking(owner: Any? = null) {
    while (!tryLock(owner));
}

inline fun <T> Mutex.withLockBlocking(owner: Any? = null, action: () -> T): T {
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