package com.josebraz.concurrent_ext

import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ClassifiedInterceptorTest {

    @Test
    fun simpleEmitAndNotifyTest() = runTest {
        val interceptor = ClassifiedInterceptor<Long, Int>(-1)

        var counter = 0
        val list = List(100) { id ->
            async(UnconfinedTestDispatcher()) {
                val result = interceptor.request(id.toLong())
                assertEquals(id, result.getOrThrow())
                counter++
            }
        }

        repeat(100) { id ->
            interceptor.notifyResponse(id.toLong(), Result.success(id))
        }

        list.awaitAll()
        assertEquals(100, counter)
    }

    @Test
    fun multipleEmitWithTheSameIdTest() = runTest {
        val interceptor = ClassifiedInterceptor<Long, Int>(-1)

        var counter = 0
        val list = List(100) { id ->
            async(UnconfinedTestDispatcher()) {
                val result = interceptor.request(1L)
                assertEquals(id, result.getOrThrow())
                counter++
            }.also {
                launch {
                    interceptor.notifyResponse(1L, Result.success(id))
                }
            }
        }

        list.awaitAll()
        assertEquals(100, counter)
    }

    @Test
    fun simulateServerCommunicationTest() = runTest {
        val mockServer = object {
            var onMessage: ((result: String) -> Unit)? = null
            fun send(request: String) {
                launch {
                    // simulate server process
                    delay(100)
                    val (messageId, message) = request.split("|")
                    onMessage?.invoke("$messageId|AAAAA")
                }
            }
        }

        val interceptor = ClassifiedInterceptor<Long, String>(-1)

        mockServer.onMessage = { result: String ->
            // simulate local process
            val messageId = result.takeWhile { it != '|' }.toLong()
            interceptor.notifyResponse(messageId, Result.success(result))
        }

        val result = async(UnconfinedTestDispatcher()) {
            val messageId = 1L
            val result = interceptor.request(messageId) {
                mockServer.send("$messageId|LALALA")
            }
            result.getOrNull()
        }.await()

        assertEquals("1|AAAAA", result)
    }
}