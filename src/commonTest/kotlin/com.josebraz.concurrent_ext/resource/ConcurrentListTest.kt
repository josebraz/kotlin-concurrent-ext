package com.josebraz.concurrent_ext.resource

import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentListTest {

    @Test
    fun testAddAndRetry() {
        val list = ConcurrentList<Int>()
        list.add(1)
        list.add(2)
        list.add(3)
        list.add(4)

        assertEquals(4, list.size)
        assertEquals(1, list[0])
        assertEquals(2, list[1])
        assertEquals(3, list[2])
        assertEquals(4, list[3])
    }

    @Test
    fun testIterator() {
        val list = ConcurrentList<Int>()
        list.add(1)
        list.add(2)
        list.add(3)
        list.add(4)

        assertEquals(4, list.size)
        assertEquals(1, list[0])
        assertEquals(2, list[1])
        assertEquals(3, list[2])
        assertEquals(4, list[3])
    }
}