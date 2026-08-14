package works.bru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempThreadIdTest {
    @Test
    fun neverCollidesWithAProviderThreadId() {
        listOf("+4799999999", "Vipps", "").forEach {
            assertTrue(it, tempThreadId(it) < 0)
        }
    }

    @Test
    fun sameAddressGetsTheSameThread() {
        assertEquals(tempThreadId("+4799999999"), tempThreadId("+4799999999"))
    }

    @Test
    fun differentAddressesGetDifferentThreads() {
        assertNotEquals(tempThreadId("+4799999999"), tempThreadId("+4788888888"))
    }
}
