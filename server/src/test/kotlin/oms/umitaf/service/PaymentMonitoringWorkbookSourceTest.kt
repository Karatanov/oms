package oms.umitaf.service

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentMonitoringWorkbookSourceTest {
    @Test
    fun `bundled source has the approved payment indicators`() {
        val items = PaymentMonitoringWorkbookSource.bundledSnapshot()

        assertEquals("VN08_01", items.first().code)
        assertEquals(27_557_342.0, items.first().totalInvestmentUah)
    }

    @Test
    fun `parses source indicators and ignores total row`() {
        val csv = """
            UR III SP Payment Monitoring,,,,,,,,,,
            ID,"Total Investment Cost, UAH","Investment Cost, UAH (excl. local co-financing)","Local co-financing, UAH",,"Amount paid + in progress, Total UAH","Amount paid, Total UAH","Remaining payment under SP, UAH","EIB share planned, %","EIB share factual, %",EIB share compliance
            TOTAL:,,,,,,,,,,
            VN08_01,"1 000,00","900,00","100,00",,"500,00","450,00","550,00",90%,89.5%,Yes
        """.trimIndent()

        val items = PaymentMonitoringWorkbookSource.parseCsv(csv)

        assertEquals(1, items.size)
        assertEquals("VN08_01", items.single().code)
        assertEquals(1_000.0, items.single().totalInvestmentUah)
        assertEquals(450.0, items.single().paidUah)
        assertEquals("Yes", items.single().eibShareCompliance)
    }
}
