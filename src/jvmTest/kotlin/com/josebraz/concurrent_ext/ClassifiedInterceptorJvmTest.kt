package com.josebraz.concurrent_ext

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

@ExperimentalCoroutinesApi
class ClassifiedInterceptorJvmTest {

    @Test
    fun assertTimeoutOnTwoEmits(): Unit = runBlocking {
        val interceptor = ClassifiedInterceptor<Long, Int>(-1, 100)
        async { interceptor.sendRequest(1L) }

        val result2 = async {
            interceptor.sendRequest(1L)
        }.await()

        System.currentTimeMillis()

        assertThrows<TimeoutCancellationException> { result2.getOrThrow() }
        this.coroutineContext.cancelChildren()
    }
}

open class Debouncer<T>(
    protected val scope: CoroutineScope,
    protected val period: Long
) {
    protected val mutex = Mutex()
    protected var scheduleJob: Job? = null
    protected var lastRan: Long = -period

    fun debounce(
        arg: T,
        block: suspend CoroutineScope.(T) -> Unit
    ): Boolean {
        synchronized(this@Debouncer) {
            scheduleJob?.cancel()
            scheduleJob = scope.launch {
                delay(period)
                synchronized(this@Debouncer) {
                    scheduleJob = null
                    lastRan = System.currentTimeMillis()
                }
                block(arg)
            }
        }
        return true
    }
}

private open class Throttler<T>(
    scope: CoroutineScope,
    period: Long
): Debouncer<T>(scope, period) {

    fun throttle(
        arg: T,
        block: suspend CoroutineScope.(T) -> Unit
    ): Boolean {
        synchronized(this@Throttler) {
            if (lastRan + period <= System.currentTimeMillis()) {
                lastRan = System.currentTimeMillis()
                scheduleJob?.cancel()
                scheduleJob = null
                scope.launch { block(arg) }
                return true
            }
        }
        debounce(arg, block)
        return false
    }
}
