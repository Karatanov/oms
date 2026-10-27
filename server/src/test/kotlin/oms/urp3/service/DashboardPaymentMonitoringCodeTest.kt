package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardPaymentMonitoringCodeTest {
    @Test
    fun `only primary monitoring rows contribute to the eib payment KPI`() {
        assertTrue(isPrimaryPaymentMonitoringSubprojectCode("VN08_01"))
        assertTrue(isPrimaryPaymentMonitoringSubprojectCode("KH08_07#Lot1"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_W"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_TS"))
        assertFalse(isPrimaryPaymentMonitoringSubprojectCode("VN08_01_CSC"))
    }
}
