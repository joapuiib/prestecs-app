package com.fpmislata.prestecs.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanDebouncerTest {
    private var time = 0L
    private val debouncer = ScanDebouncer(windowMillis = 1_500, now = { time })

    private fun readAt(millis: Long, value: String): Boolean {
        time = millis
        return debouncer.accept(value)
    }

    @Test
    fun `a code held in front of the camera counts once`() {
        val accepted = (0L..5_000L step 200).map { readAt(it, "C1 - P01") }

        assertEquals(listOf(true), accepted.filter { it })
    }

    @Test
    fun `the same code counts again after being out of view`() {
        readAt(0, "C1 - P01")

        assertEquals(false, readAt(1_000, "C1 - P01"))
        assertEquals(true, readAt(2_600, "C1 - P01"))
    }

    @Test
    fun `a different code always counts`() {
        readAt(0, "C1 - P01")

        assertEquals(true, readAt(100, "C1 - P02"))
        assertEquals(true, readAt(200, "C1 - P01"))
    }

    @Test
    fun `reset lets the last code count again at once`() {
        readAt(0, "C1 - P01")

        debouncer.reset()

        assertEquals(true, readAt(100, "C1 - P01"))
    }
}
