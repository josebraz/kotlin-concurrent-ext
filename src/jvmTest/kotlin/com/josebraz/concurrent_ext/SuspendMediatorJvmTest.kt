package com.josebraz.concurrent_ext

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class SuspendMediatorJvmTest {

    @Test
    fun assertTimeoutOnTwoEmits(): Unit = runBlocking {
        val interceptor = SuspendMediator<Long, Int>(200, 100)
        val job = async(UnconfinedTestDispatcher()) { interceptor.suspend(1L) }
        val result2 = interceptor.suspend(1L)

        assertThrows<TimeoutCancellationException> { result2.getOrThrow() }
        assertEquals(0, (interceptor as QueueSuspendMediator<*, *>).awaitQueue.size)
        assertEquals(1, (interceptor as NoQueueSuspendMediator<*, *>).messages.size)

        val result1 = job.await()
        assertThrows<TimeoutCancellationException> { result1.getOrThrow() }
        this.coroutineContext.cancelChildren()
    }
}
