package com.josebraz.concurrent_ext.performance

import com.josebraz.concurrent_ext.withLockBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock

/**
 *
 * REGULAR :  ||| | |     |||||| || ||||       |||| ||| ||||||||||||||||
 * THROTTLE:  |    |    |    |    |    |       |    |    |    |    |    |
 * DEBOUNCE:            |                  |                               |
 *
 */

interface Debouncer<T> {
    fun debounce(arg: T, block: suspend CoroutineScope.(T) -> Unit): Boolean
    fun cancel()
}

interface Throttler<T> {
    fun throttle(arg: T, block: suspend CoroutineScope.(T) -> Unit): Boolean
    fun cancel()
}

interface ThrottlerUnit {
    fun throttle(block: suspend CoroutineScope.(Unit) -> Unit): Boolean
    fun cancel()
}

interface DebouncerUnit {
    fun debounce(block: suspend CoroutineScope.(Unit) -> Unit): Boolean
}

/////////////////////////////////////////////////////////////////////////////

@Suppress("FunctionName")
fun Debouncer(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): DebouncerUnit = object : DebouncerImpl<Unit>(scope, period, currentTime), DebouncerUnit {
    override fun debounce(block: suspend CoroutineScope.(Unit) -> Unit): Boolean {
        return this.debounce(Unit, block)
    }
}

@Suppress("FunctionName")
fun <T> Debouncer(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): Debouncer<T> = DebouncerImpl(scope, period, currentTime)

@Suppress("FunctionName")
fun CoroutineScope.Debouncer(
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    block: suspend CoroutineScope.(Unit) -> Unit
): () -> Boolean {
    val debouncer = Debouncer(this, period, currentTime)
    return { debouncer.debounce(block) }
}

@Suppress("FunctionName")
fun <T> CoroutineScope.Debouncer(
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    block: suspend CoroutineScope.(T) -> Unit
): (T) -> Boolean {
    val debouncer = DebouncerImpl<T>(this, period, currentTime)
    return { arg: T ->
        debouncer.debounce(arg, block)
    }
}

@Suppress("FunctionName")
fun Throttler(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): ThrottlerUnit = object : ThrottlerImpl<Unit>(scope, period, currentTime), ThrottlerUnit {
    override fun throttle(block: suspend CoroutineScope.(Unit) -> Unit): Boolean {
        return this.throttle(Unit, block)
    }
}

@Suppress("FunctionName")
fun <T> Throttler(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): Throttler<T> = ThrottlerImpl(scope, period, currentTime)

@Suppress("FunctionName")
fun CoroutineScope.Throttler(
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    block: suspend CoroutineScope.(Unit) -> Unit
): () -> Unit {
    val throttler = Throttler(this, period, currentTime)
    return { throttler.throttle(block) }
}

@Suppress("FunctionName")
fun <T> CoroutineScope.Throttler(
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    block: suspend CoroutineScope.(T) -> Unit
): (T) -> Unit {
    val throttler = ThrottlerImpl<T>(this, period, currentTime)
    return { arg: T ->
        throttler.throttle(arg, block)
    }
}

fun <T> Flow<T>.throttleFlow(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): Flow<T> = channelFlow {
    val throttler = Throttler(scope, period, currentTime)
    collect { value ->
        throttler.throttle {
            send(value)
        }
    }
}

/////////////////////////////////////////////////////////////////////////////////

private open class DebouncerImpl<T>(
    protected val scope: CoroutineScope,
    protected val period: Long,
    protected val currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): Debouncer<T> {
    protected val mutex = Mutex()
    protected var scheduleJob: Job? = null
    protected var lastRan: Long = -period

    override fun debounce(
        arg: T,
        block: suspend CoroutineScope.(T) -> Unit
    ): Boolean {
        mutex.withLockBlocking {
            scheduleJob?.cancel()
            scheduleJob = scope.launch {
                delay(period)
                mutex.withLockBlocking {
                    scheduleJob = null
                    lastRan = currentTime()
                }
                block(arg)
            }
        }
        return true
    }

    override fun cancel() {
        mutex.withLockBlocking {
            scheduleJob?.cancel()
            scheduleJob = null
        }
    }
}

private open class ThrottlerImpl<T>(
    scope: CoroutineScope,
    period: Long,
    currentTime: () -> Long = { Clock.System.now().toEpochMilliseconds() }
): DebouncerImpl<T>(scope, period, currentTime), Throttler<T> {

    override fun throttle(
        arg: T,
        block: suspend CoroutineScope.(T) -> Unit
    ): Boolean {
        mutex.withLockBlocking {
            if (lastRan + period <= currentTime()) {
                lastRan = currentTime()
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
