package com.josebraz.concurrent_ext

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

interface SuspendMediator<K, V> {

    /**
     * Allocate key on queue (if have) and when success calls this action callback.
     * The action is frequently a socket send call or something like that.
     *
     * This method suspends until the resume method with the same key is called or on timeout
     */
    suspend fun suspend(key: K, action: () -> Unit = {}): Result<V>

    /**
     * Resume the suspend execution with result.
     * Returns true if any execution is awaiting for this key or false otherwise.
     */
    fun resume(key: K, result: Result<V>): Boolean
}

enum class QueueMode {
    NO_QUEUE,
    AWAIT_SAME_KEY,
    ENSURE_ORDER
}

const val NoTimeout: Long = -1L
fun <K, V> SuspendMediator(
    resultTimeoutMillis: Long = NoTimeout,
    queueTimeoutMillis: Long = NoTimeout,
    queueMode: QueueMode = QueueMode.ENSURE_ORDER
): SuspendMediator<K, V> {
    return when (queueMode) {
        QueueMode.ENSURE_ORDER -> {
            OrderedSuspendMediator(resultTimeoutMillis, queueTimeoutMillis)
        }
        QueueMode.AWAIT_SAME_KEY -> {
            AwaitSameKeySuspendMediator(resultTimeoutMillis, queueTimeoutMillis)
        }
        QueueMode.NO_QUEUE -> {
            NoQueueSuspendMediator(resultTimeoutMillis)
        }
    }
}

internal open class NoQueueSuspendMediator<K, V>(
    private val timeoutMillis: Long
): SuspendMediator<K, V> {
    // all requests awaiting response
    val messages = LinkedHashMap<K, CancellableContinuation<Result<V>>?>()
    val messagesMutex: Mutex = Mutex()

    protected open suspend fun awaitAndAllocateKey(key: K) {
        val allocated = messagesMutex.withLock {
            val canAllocateSpace = key !in messages
            if (key !in messages) { // allocate key in map to start request immediately
                messages[key] = null
            }
            return@withLock canAllocateSpace
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

    override fun resume(key: K, result: Result<V>): Boolean {
        val entry = messagesMutex.withLockBlocking {
            messages.remove(key)
        } ?: return false
        entry.resume(result)
        return true
    }
}

internal data class AwaitRequestEntry<K>(
    val key: K,
    val continuation: CancellableContinuation<Unit>
)

internal abstract class QueueSuspendMediator<K, V>(
    timeoutMillis: Long,
    private val queueTimeoutMillis: Long
): NoQueueSuspendMediator<K, V>(timeoutMillis) {

    val awaitQueue = mutableListOf<AwaitRequestEntry<K>>()
    val awaitQueueMutex: Mutex = Mutex()

    override suspend fun awaitAndAllocateKey(key: K) {
        val allocated = messagesMutex.withLock {
            val needAwait = needAwait(key, messages.keys)
            val canAllocateSpace = !needAwait
            if (canAllocateSpace) { // allocate key in map to start request immediately
                messages[key] = null
            }
            return@withLock canAllocateSpace
        }
        if (!allocated) {
            var entry: AwaitRequestEntry<K>? = null
            try {
                suspendCoroutineWithTimeout<Unit>(queueTimeoutMillis) { continuation ->
                    awaitQueueMutex.withLockBlocking {
                        awaitQueue.add(AwaitRequestEntry(key, continuation).also { entry = it })
                    }
                }
            } catch (e: Exception) {
                entry?.let {
                    awaitQueueMutex.withLock {
                        awaitQueue.remove(it)
                    }
                }
                throw e
            }
        }
    }

    abstract fun needAwait(key: K, messages: Set<K>): Boolean

    abstract fun resumeWaiting(key: K)

    override fun resume(key: K, result: Result<V>): Boolean {
        val entry = messagesMutex.withLockBlocking {
            messages.remove(key)?.also {
                resumeWaiting(key)
            }
        } ?: return false
        entry.resume(result)
        return true
    }
}

private class OrderedSuspendMediator<K, V>(
    timeoutMillis: Long,
    queueTimeoutMillis: Long
): QueueSuspendMediator<K, V>(timeoutMillis, queueTimeoutMillis) {

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
): QueueSuspendMediator<K, V>(timeoutMillis, queueTimeoutMillis) {

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
