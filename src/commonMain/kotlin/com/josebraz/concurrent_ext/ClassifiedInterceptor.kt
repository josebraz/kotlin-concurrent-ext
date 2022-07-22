package com.josebraz.concurrent_ext

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume

interface ClassifiedInterceptor<K, V> {

    /**
     * Emit a new input with key and return the result.
     *
     * Awaits all emits of the same key that were called
     * before and are not completed yet to ensure order.
     *
     * This suspend function resumes when:
     *   1. The notify method is called and this emit
     *      is the older with this same key
     *   2. Timeout
     */
    suspend fun request(key: K, onAwait: () -> Unit = {}): Result<V>

    fun notifyResponse(key: K, output: Result<V>): Boolean
}

fun <K, V> ClassifiedInterceptor(
    timeMillis: Long = 10_000L,
    maxWaitToRequestMillis: Long = -1L
): ClassifiedInterceptor<K, V> {
    return ClassifiedInterceptorImpl(timeMillis, maxWaitToRequestMillis)
}

class ClassifiedInterceptorImpl<K, V>(
    private val timeMillis: Long,
    private val maxWaitToRequestMillis: Long
): ClassifiedInterceptor<K, V> {
    private val messages = mutableMapOf<K, CancellableContinuation<Result<V>>?>()
    private val awaitList = mutableListOf<Pair<K, CancellableContinuation<Unit>>>()
    private val messagesMutex: Mutex = Mutex()
    private val awaitListMutex: Mutex = Mutex()

    private suspend fun awaitAndAllocateKey(key: K) {
        return suspendCoroutineWithTimeout(maxWaitToRequestMillis) { continuation ->
            val allocated = messagesMutex.withLockBlocking {
                if (key !in messages.keys) {
                    messages[key] = null
                    true
                } else {
                    false
                }
            }
            if (allocated) {
                continuation.resume(Unit)
            } else {
                awaitListMutex.withLockBlocking {
                    awaitList.add(key to continuation)
                }
            }
        }
    }

    private fun resumeWaiting(key: K) {
        awaitListMutex.withLockBlocking {
            awaitList.firstOrNull()?.let { (awaitKey, continuation) ->
                if (key == awaitKey) {
                    awaitList.removeFirst()
                    continuation.resume(Unit)
                }
            }
        }
    }

    override suspend fun request(key: K, onAwait: () -> Unit): Result<V> {
        try {
            awaitAndAllocateKey(key)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return suspendCoroutineWithTimeout(timeMillis) { cont ->
            try {
                messages[key] = cont
                onAwait.invoke()
            } catch (e: Exception) {
                notifyResponse(key, Result.failure(e))
            }
        }
    }

    override fun notifyResponse(key: K, output: Result<V>): Boolean {
        val entry = messagesMutex.withLockBlocking {
            messages.remove(key)?.also {
                resumeWaiting(key)
            }
        } ?: return false
        entry.resume(output)
        return true
    }
}