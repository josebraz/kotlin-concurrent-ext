package com.josebraz.concurrent_ext

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.resume

interface SuspendMediator<K, V> {

    suspend fun suspend(key: K, action: () -> Unit = {}): Result<V>

    fun resume(key: K, output: Result<V>): Boolean
}

enum class QueueMode {
    NO_AWAIT,
    AWAIT_SAME_KEY,
    ENSURE_ORDER
}

const val NoTimeout: Long = -1L
fun <K, V> SuspendMediator(
    timeoutMillis: Long = NoTimeout,
    queueTimeoutMillis: Long = NoTimeout,
    queueMode: QueueMode = QueueMode.ENSURE_ORDER
): SuspendMediator<K, V> {
    return when (queueMode) {
        QueueMode.ENSURE_ORDER -> {
            OrderedSuspendMediator(timeoutMillis, queueTimeoutMillis)
        }
        QueueMode.AWAIT_SAME_KEY -> {
            AwaitSameKeySuspendMediator(timeoutMillis, queueTimeoutMillis)
        }
        QueueMode.NO_AWAIT -> {
            NoAwaitSuspendMediator(timeoutMillis)
        }
    }
}

private open class NoAwaitSuspendMediator<K, V>(
    private val timeoutMillis: Long
): SuspendMediator<K, V> {
    protected val messages = LinkedHashMap<K, CancellableContinuation<Result<V>>?>()
    protected val messagesMutex: Mutex = Mutex()

    protected open suspend fun awaitAndAllocateKey(key: K) {
        val allocated = messagesMutex.withLockBlocking {
            val canAllocateSpace = key !in messages
            if (key !in messages) { // allocate key in map to start request immediately
                messages[key] = null
            }
            return@withLockBlocking canAllocateSpace
        }
        if (!allocated) {
            throw Exception("Key $key already is waiting")
        }
    }

    override suspend fun suspend(key: K, action: () -> Unit): Result<V> {
        try {
            awaitAndAllocateKey(key)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return suspendCoroutineWithTimeout(timeoutMillis) { cont ->
            try {
                messages[key] = cont
                action.invoke()
            } catch (e: Exception) {
                resume(key, Result.failure(e))
            }
        }
    }

    override fun resume(key: K, output: Result<V>): Boolean {
        val entry = messagesMutex.withLockBlocking {
            messages.remove(key)
        } ?: return false
        entry.resume(output)
        return true
    }
}

internal data class AwaitRequestEntry<K>(
    val key: K,
    val continuation: CancellableContinuation<Unit>
)

private abstract class AwaitSuspendMediator<K, V>(
    timeoutMillis: Long,
    private val queueTimeoutMillis: Long
): NoAwaitSuspendMediator<K, V>(timeoutMillis) {

    val awaitQueue = mutableListOf<AwaitRequestEntry<K>>()
    val awaitQueueMutex: Mutex = Mutex()

    override suspend fun awaitAndAllocateKey(key: K) {
        return suspendCoroutineWithTimeout(queueTimeoutMillis) { continuation ->
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

    override fun resume(key: K, output: Result<V>): Boolean {
        val entry = messagesMutex.withLockBlocking {
            messages.remove(key)?.also {
                resumeWaiting(key)
            }
        } ?: return false
        entry.resume(output)
        return true
    }
}

private class OrderedSuspendMediator<K, V>(
    timeoutMillis: Long,
    queueTimeoutMillis: Long
): AwaitSuspendMediator<K, V>(timeoutMillis, queueTimeoutMillis) {

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

private class AwaitSameKeySuspendMediator<K, V>(
    timeoutMillis: Long,
    queueTimeoutMillis: Long
): AwaitSuspendMediator<K, V>(timeoutMillis, queueTimeoutMillis) {

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
