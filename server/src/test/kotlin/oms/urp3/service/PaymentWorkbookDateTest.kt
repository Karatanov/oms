package oms.urp3.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.io.ByteArrayOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class PaymentWorkbookDateTest {
    @Test fun `workbook import includes US dates but excludes planned payment notes`() {
        val bytes = XSSFWorkbook().use { book ->
            book.createSheet("UR III SP Payment Monitoring").apply {
                createRow(0).apply {
                    listOf("ID", "Amount paid, Total UAH", "Remaining payment under SP, UAH").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
                }
                createRow(1).apply { createCell(0).setCellValue("ZP08_04"); createCell(1).setCellValue(1.0) }
            }
            book.createSheet("UR III APRIL 2026 payments").apply {
                createRow(0).apply {
                    listOf("ID", "Date", "Amount, UAH").forEachIndexed { i, v -> createCell(i).setCellValue(v) }
                }
                listOf("4/14/2026" to 377224.28, "4/24/2026" to 51290.0,
                    "4/24/2026" to 3529443.71, "Planned" to 999.0, "Sent for payment 05.11.2026" to 888.0).forEachIndexed { i, (date, amount) ->
                    createRow(i + 1).apply {
                        createCell(0).setCellValue("ZP08_04_W"); createCell(1).setCellValue(date); createCell(2).setCellValue(amount)
                    }
                }
            }
            ByteArrayOutputStream().use { out -> book.write(out); out.toByteArray() }
        }
        val rows = PaymentMonitoringWorkbookSource.parseWorkbookData(bytes).transactions
        assertEquals(3, rows.size)
        assertEquals(3_957_957.99, rows.sumOf { it.amountUah }, 0.001)
        assertEquals(listOf(14, 24, 24), rows.map { it.paymentDate.dayOfMonth })
    }
    @Test fun `approved April source text dates remain real dated payments`() {
        assertEquals(LocalDate.of(2026, 4, 14), parsePaymentWorkbookDate("4/14/2026"))
        assertEquals(LocalDate.of(2026, 4, 24), parsePaymentWorkbookDate(" 4/24/2026 "))
        assertEquals(LocalDate.of(2026, 4, 5), parsePaymentWorkbookDate("4/5/2026"))
        assertEquals(LocalDate.of(2026, 4, 24), parsePaymentWorkbookDate("2026-04-24"))
    }
    @Test fun `pending and invalid dates are not converted into paid transactions`() {
        listOf("", "Planned", "Sent for payment 05.11.2026", "2/30/2026", "13/4/2026", "2/29/2025").forEach {
            assertNull(parsePaymentWorkbookDate(it), it)
        }
        assertEquals(LocalDate.of(2024, 2, 29), parsePaymentWorkbookDate("2/29/2024"))
    }
}
