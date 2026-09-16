package works.bru

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumbersTest {
    @Test
    fun bareNationalWithoutSimStaysAsTyped() {
        assertEquals("999 99 999", PhoneNumbers.normalizeE164("999 99 999", null))
    }

    @Test
    fun internationalKeepsItsPrefix() {
        assertEquals("+4799999999", PhoneNumbers.normalizeE164("+47 999 99 999", null))
    }

    @Test
    fun doubleZeroBecomesPlus() {
        assertEquals("+4799999999", PhoneNumbers.normalizeE164("004799999999", null))
    }

    @Test
    fun alphanumericSenderPassesThrough() {
        assertEquals("Vipps", PhoneNumbers.normalizeE164("Vipps", null))
    }

    @Test
    fun typeMapsToDirection() {
        assertEquals("in", PhoneNumbers.directionFromType(1))
        assertEquals("out", PhoneNumbers.directionFromType(2))
    }
}
