package com.josebraz.concurrent_ext

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume

interface ClassifiedInterceptor<K, V> {

    suspend fun sendRequest(key: K, sendAction: () -> Unit = {}): Result<V>

    fun notifyResponse(key: K, output: Result<V>): Boolean
}

enum class QueueMode {
    AWAIT_SAME_KEY,
    ENSURE_ORDER
}

const val NoTimeout: Long = -1L
fun <K, V> ClassifiedInterceptor(
    timeoutMillis: Long = NoTimeout,
    maxWaitToRequestMillis: Long = NoTimeout,
    queueMode: QueueMode = QueueMode.ENSURE_ORDER
): ClassifiedInterceptor<K, V> {
    return when (queueMode) {
        QueueMode.ENSURE_ORDER -> {
            OrderedClassifiedInterceptor(timeoutMillis, maxWaitToRequestMillis)
        }
        QueueMode.AWAIT_SAME_KEY -> {
            AwaitSameKeyClassifiedInterceptor(timeoutMillis, maxWaitToRequestMillis)
        }
    }
}

internal data class AwaitRequestEntry<K>(
    val key: K,
    val continuation: CancellableContinuation<Unit>
)

private abstract class BaseClassifiedInterceptor<K, V>(
    private val timeoutMillis: Long,
    private val maxWaitToRequestMillis: Long
): ClassifiedInterceptor<K, V> {
    private val messages = LinkedHashMap<K, CancellableContinuation<Result<V>>?>()
    private val messagesMutex: Mutex = Mutex()

    val awaitQueue = mutableListOf<AwaitRequestEntry<K>>()
    val awaitQueueMutex: Mutex = Mutex()

    private suspend fun awaitAndAllocateKey(key: K) {
        return suspendCoroutineWithTimeout(maxWaitToRequestMillis) { continuation ->
            val allocated = messagesMutex.withLockBlocking {
                val needAwait = needAwait(key, messages.keys)
                val canAllocateSpace = !needAwait
                if (canAllocateSpace) { // allocate key in map to start request immediately
                    messages[key] = null
                }
                return@withLockBlocking canAllocateSpace
            }
            if (allocated) {
                continuation.resume(Unit)
            } else {
                awaitQueueMutex.withLockBlocking {
                    awaitQueue.add(AwaitRequestEntry(key, continuation))
                }
            }
        }
    }

    abstract fun needAwait(key: K, messages: Set<K>): Boolean

    abstract fun resumeWaiting(key: K)

    override suspend fun sendRequest(key: K, sendAction: () -> Unit): Result<V> {
        try {
            awaitAndAllocateKey(key)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return suspendCoroutineWithTimeout(timeoutMillis) { cont ->
            try {
                messages[key] = cont
                sendAction.invoke()
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

private class OrderedClassifiedInterceptor<K, V>(
    timeoutMillis: Long,
    maxWaitToRequestMillis: Long
): BaseClassifiedInterceptor<K, V>(timeoutMillis, maxWaitToRequestMillis) {

    override fun needAwait(key: K, messages: Set<K>): Boolean {
        // To ensure order all older requests must be sent
        return awaitQueue.isNotEmpty() || key in messages
    }

    override fun resumeWaiting(key: K) {
        // resume the first awaiting request ignoring received key
        awaitQueueMutex.withLockBlocking {
            awaitQueue.removeFirstOrNull()?.continuation?.resume(Unit)
        }
    }
}

private class AwaitSameKeyClassifiedInterceptor<K, V>(
    timeoutMillis: Long,
    maxWaitToRequestMillis: Long
): BaseClassifiedInterceptor<K, V>(timeoutMillis, maxWaitToRequestMillis) {

    override fun needAwait(key: K, messages: Set<K>): Boolean {
        return key in messages
    }

    override fun resumeWaiting(key: K) {
        // resume the first awaiting request that has the same key
        awaitQueueMutex.withLockBlocking {
            awaitQueue.removeFirstOrNull { key == it.key }?.continuation?.resume(Unit)
        }
    }
}