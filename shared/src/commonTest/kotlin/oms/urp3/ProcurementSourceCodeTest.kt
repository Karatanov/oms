package oms.urp3

import kotlin.test.*

class ProcurementSourceCodeTest {
    @Test fun originalIdentifiersAreNeverTranslatedOrReconstructed() {
        for (code in listOf("VN08_01_W", "KH08_07#Lot1_TS", "VN08_01_CSC\$2")) {
            assertEquals(code, procurementSourceCode(code, "KH08_07#Lot1", "VN08_01"))
        }
        assertEquals("KH08_07#Lot1", procurementSourceCode(null, "KH08_07#Lot1", "KH08_07"))
        assertEquals("VN08_01", procurementSourceCode("", null, "VN08_01"))
    }

    @Test fun typeComesFromFinalContractSuffixIncludingLotsAndAttempts() {
        assertEquals("W", procurementSourceType("VN08_01_W"))
        assertEquals("TS", procurementSourceType("KH08_07#Lot1_TS\$2"))
        assertEquals("CSC", procurementSourceType("VN08_01_csc"))
        assertNull(procurementSourceType("VN08_01"))
        assertNull(procurementSourceType("VN08_01_UNKNOWN"))
    }
}
