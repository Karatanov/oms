package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentMonitoringTableSelectionTest {
    private fun item(code: String, value: Double? = null, compliance: String? = null) = PaymentMonitoringWorkbookSource.Item(
        code, value, value, value, value, value, value, value, value, compliance)

    @Test fun codeSearchIsCaseInsensitiveAndExportsEveryMatchingPageWithoutMutation() {
        val source = (1..45).map { item("KH08_${it.toString().padStart(2, '0')}", it.toDouble()) } + item("VN08_01", 2.0)
        val result = selectPaymentMonitoringExportItems(source, paymentMonitoringExportQuery("  kh08_  ", "Paid", "false"))
        assertEquals(45, result.size)
        assertEquals("KH08_45", result.first().code)
        assertEquals("KH08_01", result.last().code)
        assertEquals("KH08_01", source.first().code)
        assertEquals(46, source.size)
    }

    @Test fun allNumericColumnsSortNumericallyAndKeepMissingValuesLastInBothDirections() {
        val source = listOf(item("ten", 10.0), item("missing"), item("two", 2.0), item("zero", 0.0))
        for (column in PaymentMonitoringExportSort.entries.filter { it !in setOf(PaymentMonitoringExportSort.Code, PaymentMonitoringExportSort.Compliance) }) {
            for (ascending in listOf(true, false)) {
                val query = paymentMonitoringExportQuery(null, column.name, ascending.toString())
                assertEquals(if (ascending) listOf("zero", "two", "ten", "missing") else listOf("ten", "two", "zero", "missing"),
                    selectPaymentMonitoringExportItems(source, query).map { it.code }, column.name)
            }
        }
    }

    @Test fun textSortingAndEmptyComplianceMatchTheTable() {
        val source = listOf(item("B", compliance = "z"), item("a", compliance = "A"), item("c"), item("d", compliance = ""))
        assertEquals(listOf("a", "B", "c", "d"), selectPaymentMonitoringExportItems(source, paymentMonitoringExportQuery(null, null, null)).map { it.code })
        assertEquals(listOf("B", "a", "c", "d"), selectPaymentMonitoringExportItems(source, paymentMonitoringExportQuery(null, "Compliance", "false")).map { it.code })
    }

    @Test fun emptySearchResultDoesNotFallBackToTheWholeRegister() {
        assertEquals(emptyList(), selectPaymentMonitoringExportItems(listOf(item("KH08_01")), paymentMonitoringExportQuery("VN08", null, null)))
    }

    @Test fun malformedSelectionIsRejectedBeforeExport() {
        assertFailsWith<IllegalArgumentException> { paymentMonitoringExportQuery("x".repeat(101), null, null) }
        assertFailsWith<IllegalArgumentException> { paymentMonitoringExportQuery(null, "unknown", null) }
        assertFailsWith<IllegalArgumentException> { paymentMonitoringExportQuery(null, null, "sideways") }
    }
}
