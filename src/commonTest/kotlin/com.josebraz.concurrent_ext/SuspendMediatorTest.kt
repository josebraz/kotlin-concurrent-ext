package com.josebraz.concurrent_ext

import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class SuspendMediatorTest {

    @Test
    fun simpleEmitAndNotifyTest() = runTest {
        val interceptor = SuspendMediator<Long, Int>()

        var counter = 0
        val list = List(100) { id ->
            async(UnconfinedTestDispatcher()) {
                val result = interceptor.suspend(id.toLong())
                assertEquals(id, result.getOrThrow())
                counter++
            }
        }

        repeat(100) { id ->
            interceptor.resume(id.toLong(), Result.success(id))
        }

        list.awaitAll()
        assertEquals(100, counter)
    }

    @Test
    fun multipleEmitWithTheSameIdTest() = runTest {
        val interceptor = SuspendMediator<Long, Int>()

        var counter = 0
        val list = List(100) { id ->
            async(UnconfinedTestDispatcher()) {
                val result = interceptor.suspend(1L)
                assertEquals(id, result.getOrThrow())
                counter++
            }.also {
                launch {
                    interceptor.resume(1L, Result.success(id))
                }
            }
        }

        list.awaitAll()
        assertEquals(100, counter)
    }

    @Test
    fun orderedQueueAwaitForAllOlderRequestTest() = runTest {
        val interceptor = SuspendMediator<Long, Int>(queueMode = QueueMode.ENSURE_ORDER)

        var counter = 0
        launch(UnconfinedTestDispatcher()) {
            launch {
                interceptor.suspend(1L)
                counter++
            }
            launch {
                interceptor.suspend(1L) // need await in queue
                counter++
            }
            launch {
                interceptor.suspend(2L) // need await second complete
                counter++
            }
        }

        val twoAwaiting1 = interceptor.resume(2L, Result.success(-1))
        interceptor.resume(1L, Result.success(-1))
        val twoAwaiting2 = interceptor.resume(2L, Result.success(-1))
        interceptor.resume(1L, Result.success(-1))
        val twoAwaiting3 = interceptor.resume(2L, Result.success(-1))

        advanceUntilIdle()
        assertFalse(twoAwaiting1)
        assertFalse(twoAwaiting2)
        assertTrue(twoAwaiting3)
        assertEquals(3, counter)
    }

    @Test
    fun awaitSameKeyQueueTest() = runTest {
        val interceptor = SuspendMediator<Long, Int>(queueMode = QueueMode.AWAIT_SAME_KEY)

        var counter = 0
        launch(UnconfinedTestDispatcher()) {
            launch {
                interceptor.suspend(1L)
                counter++
            }
            launch {
                interceptor.suspend(1L) // need await in queue
                counter++
            }
            launch {
                interceptor.suspend(2L) // no await second complete
                counter++
            }
        }

        val twoAwaiting1 = interceptor.resume(2L, Result.success(-1))
        interceptor.resume(1L, Result.success(-1))
        interceptor.resume(1L, Result.success(-1))

        advanceUntilIdle()
        assertTrue(twoAwaiting1)
        assertEquals(3, counter)
    }

    @Test
    fun simulateServerCommunicationTest() = runTest {
        val interceptor = SuspendMediator<Long, String>()
        val mockServer = object {
            fun onMessage(result: String) {
                // simulate local process
                val messageId = result.takeWhile { it != '|' }.toLong()
                interceptor.resume(messageId, Result.success(result))
            }
            fun send(request: String) {
                launch {
                    // simulate server process
                    delay(100)
                    val (messageId, message) = request.split("|")
                    onMessage("$messageId|AAAAA")
                }
            }
        }

        val result = async(UnconfinedTestDispatcher()) {
            val messageId = 1L
            val result = interceptor.suspend(messageId) {
                mockServer.send("$messageId|LALALA")
            }
            result.getOrNull()
        }.await()

        assertEquals("1|AAAAA", result)
    }
}