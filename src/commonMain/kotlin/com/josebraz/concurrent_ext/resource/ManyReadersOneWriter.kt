package com.josebraz.concurrent_ext.resource

import com.josebraz.concurrent_ext.lockBlocking
import com.josebraz.concurrent_ext.withLockBlocking
import kotlinx.coroutines.sync.Mutex

open class ManyReadersOneWriter<WT: RT, RT>(
    private val resource: WT
) {
    private var readCount = 0
    private val readMutex = Mutex()
    private val resourceMutex = Mutex()
    private val serviceQueue = Mutex()

    fun <R> writer(block: (WT) -> R): R {
        try {
            serviceQueue.withLockBlocking {
                resourceMutex.lockBlocking()
            }
            return block.invoke(resource)
        } finally {
            resourceMutex.unlock()
        }
    }

    fun <R> reader(block: (RT) -> R): R {
        try {
            serviceQueue.withLockBlocking {
                readMutex.withLockBlocking {
                    readCount += 1
                    if (readCount == 1) {
                        resourceMutex.lockBlocking()
                    }
                }
            }
            return block.invoke(resource)
        } finally {
            readMutex.withLockBlocking {
                readCount -= 1
                if (readCount == 0) {
                    resourceMutex.unlock()
                }
            }
        }
    }
}