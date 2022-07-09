package com.josebraz.concurrent_ext.resource

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public open class SuspendManyReadersOneWriter<WT: RT, RT>(
    private val resource: WT
) {
    private var readCount = 0
    private val readMutex = Mutex()
    private val resourceMutex = Mutex()
    private val serviceQueue = Mutex()

    suspend fun <R> writer(block: suspend (WT) -> R): R {
        try {
            serviceQueue.withLock {
                resourceMutex.lock()
            }
            return block.invoke(resource)
        } finally {
            resourceMutex.unlock()
        }
    }

    suspend fun <R> reader(block: suspend (RT) -> R): R {
        try {
            serviceQueue.withLock {
                readMutex.withLock {
                    readCount += 1
                    if (readCount == 1) {
                        resourceMutex.lock()
                    }
                }
            }
            return block.invoke(resource)
        } finally {
            readMutex.withLock {
                readCount -= 1
                if (readCount == 0) {
                    resourceMutex.unlock()
                }
            }
        }
    }
}