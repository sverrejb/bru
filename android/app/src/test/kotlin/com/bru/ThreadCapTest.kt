package com.bru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadCapTest {
    @Test
    fun keepsExactlyTheLimitPerThread() {
        val seen = HashMap<Long, Int>()
        var kept = 0
        repeat(PER_THREAD_LIMIT + 25) {
            if (takeThreadSlot(seen, 7L)) kept++
        }
        assertTrue(kept == PER_THREAD_LIMIT)
    }

    @Test
    fun countsThreadsIndependently() {
        val seen = HashMap<Long, Int>()
        repeat(PER_THREAD_LIMIT) { assertTrue(takeThreadSlot(seen, 1L)) }
        assertFalse(takeThreadSlot(seen, 1L))
        assertTrue(takeThreadSlot(seen, 2L))
        assertTrue(takeThreadSlot(seen, -42L))
    }
}
