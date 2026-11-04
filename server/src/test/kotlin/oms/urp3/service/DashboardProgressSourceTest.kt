package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardProgressSourceTest {
    @Test
    fun `monitoring completion accepts both Excel and text percentage values`() {
        assertEquals(0.98, monitoringCompletionFraction("98%"))
        assertEquals(0.98, monitoringCompletionFraction("0.98"))
        assertEquals(0.122, monitoringCompletionFraction("12,2%"))
        assertEquals(0.122, monitoringCompletionFraction("12.2"))
        assertNull(monitoringCompletionFraction("#NAME?"))
        assertNull(monitoringCompletionFraction(null))
    }

    @Test
    fun `lot contract codes aggregate to their parent subproject`() {
        assertEquals("KH08_07", dashboardSubprojectCode("kh08_07#Lot2"))
        assertEquals("KH08_07", dashboardSubprojectCode(" KH08_07 "))
    }
}
