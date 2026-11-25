package oms.urp3.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentMonitoringTableSelectionTest {
    @Test fun exportPreservesProjectPopulationIncludingExplicitEmptySelection() {
        val source = listOf(item("KH08_01", 10.0), item("VN08_01", 20.0))
        assertEquals(listOf("KH08_01"), selectPaymentMonitoringExportItems(source,
            paymentMonitoringExportQuery(null, null, null, "kh08_01")).map { it.code })
        assertEquals(emptyList(), selectPaymentMonitoringExportItems(source,
            paymentMonitoringExportQuery(null, null, null, "")))
        assertEquals(emptyList(), selectPaymentMonitoringExportItems(source,
            paymentMonitoringExportQuery("VN", null, null, "KH08_01")))
    }
    private fun item(code: String, value: Double? = null, compliance: String? = null) = PaymentMonitoringWorkbookSource.Item(
        code, value, value, value, value, value, value, value, value, compliance)

    private fun payment(code: String, date: String = "2026-06-01") = PaymentMonitoringWorkbookSource.Transaction(
        "$code:$date", code, 8, java.time.LocalDate.parse(date), 100.0, 2.0, "June")

    @Test fun paymentExportIncludesLotsOnceAndHonorsEveryCashFilter() {
        val source = listOf(payment("ZH08_04#Lot3_TS"), payment("ZH08_04#Lot4_TS", "2026-06-03"),
            payment("ZH08_04_W"), payment("ZH08_04_TS", "2026-07-01"), payment("ZH08_04_TS", "2025-06-01"), payment("CK08_05_TS"))
        val query = paymentMonitoringExportQuery("zh08", null, null, "ZH08_04,ZH08_04_W,ZH08_04_TS", "2026-06", "2026", "technical_supervision")
        assertEquals(listOf("ZH08_04#Lot3_TS", "ZH08_04#Lot4_TS"), selectPaymentMonitoringExportTransactions(source, query).map { it.code })
        assertEquals(source.size, selectPaymentMonitoringExportTransactions(source, paymentMonitoringExportQuery(null, null, null)).size)
        assertEquals(emptyList(), selectPaymentMonitoringExportTransactions(source, paymentMonitoringExportQuery(null, null, null, "")))
    }

    @Test fun paymentExportIsNotLimitedToTheVisibleTablePage() {
        val source = (1..65).map { payment("KH08_${it.toString().padStart(2, '0')}_W") }
        assertEquals(65, selectPaymentMonitoringExportTransactions(source, paymentMonitoringExportQuery("kh08", null, null)).size)
    }

    @Test fun malformedCashFiltersAreRejected() {
        for (month in listOf("June", "2026-13", "2026-00")) assertFailsWith<IllegalArgumentException> {
            paymentMonitoringExportQuery(null, null, null, month = month)
        }
        for (year in listOf("oops", "0", "10000")) assertFailsWith<IllegalArgumentException> {
            paymentMonitoringExportQuery(null, null, null, year = year)
        }
        assertFailsWith<IllegalArgumentException> { paymentMonitoringExportQuery(null, null, null, purpose = "unknown") }
    }

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
