package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardPaymentMonitoringCodeTest {
    @Test
    fun `only primary monitoring rows contribute to the eib payment KPI`() {
        assertTrue(isPrimaryPaymentMonitoringSubprojectCode("VN08_01"))
        assertTrue(isPrimaryPaymentMonitoringSubprojectCode("KH08_07#Lot1"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_W"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_TS"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_CSC"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_TS\$1"))
    }

    @Test
    fun `payment type helper supports contract row identifiers`() {
        assertEquals("W", "VN08_01_W\$1".paymentMonitoringContractType())
        assertEquals("TS", "VN08_01_TS\$1".paymentMonitoringContractType())
        assertEquals("CSC", "VN08_01_CSC\$1".paymentMonitoringContractType())
        assertEquals("VN08_01", "VN08_01_TS\$1".paymentMonitoringSubprojectCode())
    }
}
