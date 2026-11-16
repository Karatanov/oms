package oms.urp3.service

import kotlin.test.*
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class PaymentSourceNumbersTest {
    private fun item(paid: String, percent: String = "90%", remaining: String = "-12,50") =
        PaymentMonitoringWorkbookSource.parseCsv("""
            ID,"Amount paid, Total UAH","Remaining payment under SP, UAH","Total Investment Cost, UAH","EIB share planned, %"
            VN08_01,"$paid","$remaining",1000,"$percent"
        """.trimIndent()).single()

    @Test fun `monetary comma grouping is not read as a decimal or silently lost`() {
        assertEquals(1234.0, item("1,234").paidUah)
        assertEquals(1234567.0, item("1,234,567").paidUah)
        assertEquals(1234567.89, item("1,234,567.89").paidUah)
        assertEquals(-1234.0, item("-1,234").paidUah)
    }

    @Test fun `localized spaces and decimal commas preserve amounts and corrections`() {
        for (value in listOf("1 234,56", "1\u00a0234,56", "1\u202f234,56")) {
            assertEquals(1234.56, item(value).paidUah, value)
        }
        assertEquals(-12.5, item("1").remainingUah)
        assertEquals(0.0, item("0").paidUah)
        assertEquals(0.995, item("0,995").paidUah)
    }

    @Test fun `percentage decimal comma is not reinterpreted as monetary grouping`() {
        assertEquals(1.234, item("1,234", "1,234%").plannedEibSharePct)
        assertEquals(0.995, item("100", "0,995%").plannedEibSharePct)
    }

    @Test fun `mixed separator conventions preserve the same monetary value`() {
        for (raw in listOf("1,234.56", "1.234,56", "+1,234.56", "+1.234,56")) {
            assertEquals(1234.56, item(raw).paidUah, raw)
        }
        assertEquals(1234567.89, item("1.234.567,89").paidUah)
        assertEquals(-1234.56, item("-1.234,56").paidUah)
        assertEquals(123456.0, item("1.234,56e2").paidUah)
        assertEquals(123456.0, item("1,234.56e2").paidUah)
    }

    @Test fun `invalid mixed grouping cannot silently create a different number`() {
        for (raw in listOf("12,34.56", "12.34,56", "1,23,456.78", "1.23.456,78", "1,234.5.6")) {
            assertFailsWith<IllegalArgumentException>(raw) { item(raw) }
        }
        assertFailsWith<IllegalArgumentException> { item("100", "12,34.56%") }
    }

    @Test fun `positive signs and multiple dot groups are unambiguous while a single dot stays decimal`() {
        assertEquals(1234.0, item("+1,234").paidUah)
        assertEquals(1234567.0, item("1.234.567").paidUah)
        assertEquals(-1234567.0, item("-1.234.567").paidUah)
        assertEquals(1.234, item("1.234").paidUah)
        assertEquals(0.995, item("0,995").paidUah)
    }

    @Test fun `nonfinite amounts are rejected and missing values are not invented`() {
        for (value in listOf("NaN", "Infinity", "-Infinity", "1e309")) {
            assertFailsWith<IllegalArgumentException>(value) { item(value) }
        }
        for (value in listOf("12,34,567", "-", "")) {
            assertNull(item(value).paidUah, value)
        }
    }

    @Test fun `workbook text cells use monetary and percentage context independently`() {
        val bytes = XSSFWorkbook().use { book ->
            val sheet = book.createSheet("UR III SP Payment Monitoring")
            sheet.createRow(0).apply {
                listOf("ID", "Amount paid, Total UAH", "Remaining payment under SP, UAH", "EIB share planned, %")
                    .forEachIndexed { index, heading -> createCell(index).setCellValue(heading) }
            }
            sheet.createRow(1).apply {
                listOf("VN08_01", "1,234", "-12,50", "1,234%")
                    .forEachIndexed { index, value -> createCell(index).setCellValue(value) }
            }
            ByteArrayOutputStream().use { output -> book.write(output); output.toByteArray() }
        }
        val row = PaymentMonitoringWorkbookSource.parseWorkbookData(bytes).items.single()
        assertEquals(1234.0, row.paidUah)
        assertEquals(-12.5, row.remainingUah)
        assertEquals(1.234, row.plannedEibSharePct)
    }
}
