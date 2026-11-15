package oms.urp3

import kotlin.test.*

class DecimalMoneyTest {
    @Test fun decimalProductsRoundHalfUpOnceIncludingNegativeAmounts() {
        assertEquals(1407L, decimalMoneyCents(0.29, 48.5))
        assertEquals(4043L, decimalMoneyCents(0.84, 48.125))
        assertEquals(7123L, decimalMoneyCents(1.48, 48.125))
        assertEquals(101L, decimalMoneyCents(1.005))
        assertEquals(-101L, decimalMoneyCents(-1.005))
        assertEquals(-4043L, decimalMoneyCents(-0.84, 48.125))
    }
    @Test fun divisionUsesExactRemainderRatherThanBinaryHalfCent() {
        assertEquals(101L, decimalMoneyCents(2.01, 2.0, true))
        assertEquals(-101L, decimalMoneyCents(-2.01, 2.0, true))
        assertEquals(33L, decimalMoneyCents(1.0, 3.0, true))
        assertEquals(10001L, decimalMoneyCents(4500.5, 45.0, true))
    }
    @Test fun scientificNotationAndTinyValuesKeepTheirScale() {
        assertEquals(100L, decimalMoneyCents(1e-20, 1e20))
        assertEquals(100L, decimalMoneyCents(1e20, 1e20, true))
        assertEquals(0L, decimalMoneyCents(1e-200))
        assertEquals(1L, decimalMoneyCents(0.005))
        assertEquals(0L, decimalMoneyCents(0.004999))
    }
    @Test fun invalidRatesNonFiniteValuesAndOverflowAreUnavailable() {
        for (rate in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(decimalMoneyCents(1.0, rate))
            assertNull(decimalMoneyCents(1.0, rate, true))
        }
        assertNull(decimalMoneyCents(Double.NaN))
        assertNull(decimalMoneyCents(Double.POSITIVE_INFINITY))
        assertNull(decimalMoneyCents(Double.MAX_VALUE))
        assertNull(decimalMoneyCents(Double.MAX_VALUE, Double.MIN_VALUE, true))
        assertEquals(9223372036854774000L, decimalMoneyCents(9.223372036854774e16))
        assertEquals(-9223372036854774000L, decimalMoneyCents(-9.223372036854774e16))
        assertNull(decimalMoneyCents(9.223372036854776e16))
        assertEquals(0L, decimalMoneyCents(-0.0))
    }
}
