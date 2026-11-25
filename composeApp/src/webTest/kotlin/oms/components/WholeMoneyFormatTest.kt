package oms.components

import kotlin.test.Test
import kotlin.test.assertEquals

class WholeMoneyFormatTest {
    @Test fun registryMoneyHidesFractionsAndFloatingPointNoise() {
        assertEquals("15\u00A0957\u00A0275", formatWholeMoney("15957275.000000002"))
        assertEquals("15\u00A0721\u00A0458", formatWholeMoney("15721458.390050001"))
        assertEquals("235\u00A0817", formatWholeMoney("235816.60994999998"))
        assertEquals("8\u00A0289\u00A0508", formatWholeMoney("8.2895081E6"))
        assertEquals("0", formatWholeMoney("0.00"))
    }

    @Test fun formattedSourceValuesAndRoundingRemainReadable() {
        assertEquals("1\u00A0235", formatWholeMoney("1\u00A0234,50"))
        assertEquals("1\u00A0235", formatWholeMoney("1\u202F234.90"))
        assertEquals("−1\u00A0235", formatWholeMoney("-1234.9"))
        assertEquals("3\u00A0000\u00A0000\u00A0000", formatWholeMoney("3000000000.01"))
    }

    @Test fun missingInvalidAndOutOfRangeValuesAreNotFabricated() {
        for (source in listOf("", "—", "not available", "NaN", "Infinity", "1e30")) {
            assertEquals(source, formatWholeMoney(source))
        }
    }
}
