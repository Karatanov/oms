package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardFundingRulesTest {
    @Test fun `physical progress uses each lot's own works contract`() {
        assertEquals("KH08_07#LOT2", worksContractSourceCode("KH08_07", "KH08_07#Lot2"))
        assertEquals("VN08_01", worksContractSourceCode(" VN08_01 ", null))
        assertEquals("VN08_01", worksContractSourceCode("VN08_01", " "))
    }
    @Test fun `approved funding uses EIB cost not total funding or works contracts`() {
        assertEquals(750.25, monitoringApprovedEibUah("""{"eibCost":"750.25","fundingTotal":"1000","constructionAmount":"600"}"""))
        assertEquals(0.0, monitoringApprovedEibUah("""{"eibCost":"0"}"""))
        assertNull(monitoringApprovedEibUah("""{"fundingTotal":"1000"}"""))
        assertNull(monitoringApprovedEibUah("""{"eibCost":"#VALUE!"}"""))
        assertNull(monitoringApprovedEibUah("not json"))
    }
    @Test fun `source amounts accept both Excel grouping and decimal commas`() {
        assertEquals(6484316.0, monitoringSourceAmount("6,484,316"))
        assertEquals(1234.56, monitoringSourceAmount("1,234.56"))
        assertEquals(1234.56, monitoringSourceAmount("1 234,56"))
        assertEquals(750.25, monitoringSourceAmount("750,25"))
        assertNull(monitoringSourceAmount("#VALUE!"))
        assertNull(monitoringSourceAmount("NaN"))
    }
}
