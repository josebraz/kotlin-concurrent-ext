package com.josebraz.concurrent_ext.performance

import com.josebraz.concurrent_ext.performance.Debouncer
import com.josebraz.concurrent_ext.performance.Throttler
import com.josebraz.concurrent_ext.performance.throttleFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ThrottlerTest {

    @Test
    fun test1() = runTest {
        val throttler = Throttler(this, 50) { currentTime }
        var counter = 0
        repeat(101) {
            throttler.throttle { counter++ }
            delay(5)
        }

        advanceUntilIdle()
        assertEquals(11, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test2() = runTest {
        val throttler = Throttler(this, 0) { currentTime }
        var counter = 0
        repeat(100) {
            throttler.throttle { counter++ }
            delay(5)
        }

        advanceUntilIdle()
        assertEquals(100, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test3() = runTest {
        val throttler = Throttler(this, 20) { currentTime }
        var counter = 0
        throttler.throttle { counter++ } // will execute
        delay(5)
        throttler.throttle { counter++ } // skip
        delay(15)
        throttler.throttle { counter++ } // will execute
        delay(5)
        throttler.throttle { counter++ } // will schedule to execute
        delay(100)
        throttler.throttle { counter++ } // will execute
        throttler.throttle { counter++ } // will schedule to execute

        advanceUntilIdle()
        assertEquals(5, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test4() = runTest {
        val throttler = Throttler( this, 20) { currentTime }
        var counter = 0
        throttler.throttle { counter++ } // will execute
        throttler.throttle { counter++ } // will schedule to execute

        advanceUntilIdle()
        assertEquals(2, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test5() = runTest {
        var counter = 0
        val incrementCounter = Throttler(20, { currentTime }) { counter++ }
        incrementCounter() // will execute
        incrementCounter() // skip
        incrementCounter() // will schedule to execute

        advanceUntilIdle()
        assertEquals(2, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test6() = runTest {
        var counter = 0
        val throttler = Throttler(20, { currentTime }) { num: Int ->
            when (counter++) {
                0 -> assertEquals(1, num)
                1 -> assertEquals(3, num)
            }
        }
        throttler(1) // will execute
        throttler(2) // skip
        throttler(3) // will schedule to execute

        advanceUntilIdle()
        assertEquals(2, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test7() = runTest {
        var counter = 0
        val debouncer = Debouncer(20, { currentTime }) { counter++ }

        debouncer() // skip
        delay(15)
        debouncer() // skip
        delay(15)
        debouncer() // will schedule to execute
        delay(21)
        debouncer() // skip
        debouncer() // skip
        debouncer() // will schedule to execute

        advanceUntilIdle()
        assertEquals(2, counter)
        this.coroutineContext.cancelChildren()
    }

    @Test
    fun test8() = runTest {
        var counter = 0
        val flow = MutableSharedFlow<Int>()
        val throttlerFlow = flow.throttleFlow( this, 20, { currentTime })

        launch(UnconfinedTestDispatcher()) {
            throttlerFlow.collect { num ->
                when (counter++) {
                    0 -> assertEquals(1, num)
                    1 -> assertEquals(3, num)
                }
            }
        }

        flow.emit(1) // will execute
        flow.emit(2) // skip
        flow.emit(3) // will schedule to execute

        advanceUntilIdle()
        assertEquals(2, counter)
        this.coroutineContext.cancelChildren()
    }
}