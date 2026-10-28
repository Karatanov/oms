package oms.urp3.service

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import org.apache.poi.xssf.usermodel.XSSFWorkbook

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

    @Test
    fun `parses the approved payment monitoring worksheet from an uploaded workbook`() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("UR III SP Payment Monitoring")
        val headers = listOf(
            "ID",
            "Total Investment Cost, UAH",
            "Investment Cost, UAH (excl. local co-financing)",
            "Local co-financing, UAH",
            "Amount paid + in progress, Total UAH",
            "Amount paid, Total UAH",
            "Remaining payment under SP, UAH",
            "EIB share planned, %",
            "EIB share factual, %",
            "EIB share compliance"
        )
        sheet.createRow(0).apply { headers.forEachIndexed { index, heading -> createCell(index).setCellValue(heading) } }
        sheet.createRow(1).apply { createCell(0).setCellValue("TOTAL:") }
        sheet.createRow(2).apply {
            createCell(0).setCellValue("VN08_01")
            createCell(1).setCellValue(1_000.0)
            createCell(2).setCellValue(900.0)
            createCell(3).setCellValue(100.0)
            createCell(4).setCellValue(500.0)
            createCell(5).setCellValue(450.0)
            createCell(6).setCellValue(550.0)
            createCell(7).setCellValue(90.0)
            createCell(8).setCellValue(89.5)
            createCell(9).setCellValue("Yes")
        }
        val bytes = ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            output.toByteArray()
        }

        val items = PaymentMonitoringWorkbookSource.parseWorkbook(bytes)

        assertEquals(1, items.size)
        assertEquals("VN08_01", items.single().code)
        assertEquals(1_000.0, items.single().totalInvestmentUah)
        assertEquals(450.0, items.single().paidUah)
        assertEquals("Yes", items.single().eibShareCompliance)
    }

    @Test
    fun `keeps dated monthly payments separate from cumulative indicators`() {
        val workbook = XSSFWorkbook()
        val monitoring = workbook.createSheet("UR III SP Payment Monitoring")
        monitoring.createRow(0).apply {
            listOf("ID", "Amount paid, Total UAH", "Remaining payment under SP, UAH").forEachIndexed { index, heading ->
                createCell(index).setCellValue(heading)
            }
        }
        monitoring.createRow(1).apply {
            createCell(0).setCellValue("VN08_01")
            createCell(1).setCellValue(450.0)
            createCell(2).setCellValue(550.0)
        }
        val monthly = workbook.createSheet("UR III OCTOBER 2026 payments")
        monthly.createRow(0).apply {
            listOf("No.", "BATCH", "ID", "Date", "Amount, UAH", "Estimated Amount, EUR").forEachIndexed { index, heading ->
                createCell(index).setCellValue(heading)
            }
        }
        monthly.createRow(1).apply {
            createCell(0).setCellValue(1.0)
            createCell(1).setCellValue(8.0)
            createCell(2).setCellValue("VN08_01_W")
            createCell(3).setCellValue("2026-10-01")
            createCell(4).setCellValue(1_000.0)
            createCell(5).setCellValue(20.0)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            workbook.use { it.write(output) }
            output.toByteArray()
        }

        val data = PaymentMonitoringWorkbookSource.parseWorkbookData(bytes)

        assertEquals(1, data.items.size)
        assertEquals(1, data.transactions.size)
        assertEquals("VN08_01_W", data.transactions.single().code)
        assertEquals(1_000.0, data.transactions.single().amountUah)
    }
}
