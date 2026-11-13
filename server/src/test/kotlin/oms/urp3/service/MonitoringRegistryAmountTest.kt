package oms.urp3.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitoringRegistryAmountTest {
    @Test fun `allocation imports explicit construction types from either header without inventing a default`() {
        val cases = listOf(
            "New construction/ Нове будівництво" to "new_construction",
            "Overhaul/ Капітальний ремонт" to "capital_repair",
            "Reconstruction/ Реконструкція" to "reconstruction",
            "Renovation" to "reconstruction",
            "Restoration/ Реставрація" to "reconstruction",
            "new_construction" to "new_construction",
            "" to null,
            "Unspecified" to null
        )
        for (header in listOf("Description of Activity", "Description of Intervention")) {
            for ((sourceValue, expected) in cases) XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("3. Batch 9 (Allocation)")
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("Sub-Project ID")
                    createCell(1).setCellValue("Batch ID")
                    createCell(2).setCellValue(header)
                }
                sheet.createRow(1).apply {
                    createCell(0).setCellValue("CH09_01")
                    createCell(1).setCellValue(9.0)
                    createCell(2).setCellValue(sourceValue)
                }
                val row = parse(workbook)
                assertEquals(sourceValue, value(row, "constructionType"))
                assertEquals(expected, row.javaClass.getDeclaredMethod("constructionType")
                    .apply { isAccessible = true }.invoke(row))
            }
        }
    }

    private fun parse(workbook: XSSFWorkbook): Any {
        val bytes = ByteArrayOutputStream().use { output -> workbook.write(output); output.toByteArray() }
        val type = Class.forName("oms.urp3.service.MonitoringWorkbookSource")
        val source = type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        val progress: (Int, String) -> Unit = { _, _ -> }
        return (type.declaredMethods.single { it.name == "parse" }.apply { isAccessible = true }
            .invoke(source, bytes, progress) as List<*>).single()!!
    }
    private fun value(row: Any, key: String): String = row.javaClass.getDeclaredMethod("value", String::class.java)
        .apply { isAccessible = true }.invoke(row, key) as String

    @Test fun `monitoring import retains numeric and cached formula cents without changing percentage display`() {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("DB Table")
            sheet.createRow(11).apply {
                createCell(0).setCellValue("Sub-Project ID")
                createCell(3).setCellValue("Sub-Project Lot ID")
            }
            val rounded = workbook.createCellStyle().apply { dataFormat = workbook.createDataFormat().getFormat("#,##0") }
            val percent = workbook.createCellStyle().apply { dataFormat = workbook.createDataFormat().getFormat("0.0%") }
            val date = workbook.createCellStyle().apply { dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd") }
            sheet.createRow(15).apply {
                createCell(0).setCellValue("VN08_01")
                createCell(3).setCellValue("VN08_01")
                createCell(24).apply { setCellValue(1234.56); cellStyle = rounded }
                createCell(28).apply { setCellValue(0.0); cellStyle = rounded }
                createCell(29).setCellValue("1 234,56")
                createCell(50).apply { setCellValue(java.time.LocalDateTime.of(2026, 11, 12, 0, 0)); cellStyle = date }
                createCell(53).apply { cellFormula = "1234.56*2"; cellStyle = rounded }
                createCell(80).apply { setCellValue(0.196); cellStyle = percent }
            }
            workbook.creationHelper.createFormulaEvaluator().evaluateAll()
            val row = parse(workbook)
            assertEquals("1234.56", value(row, "fundingTotal"))
            assertEquals("2469.12", value(row, "constructionAmount"))
            assertEquals("0", value(row, "eibCost"))
            assertEquals("1 234,56", value(row, "localCost"))
            assertEquals("", value(row, "fundingTs"))
            assertEquals("2026-11-12", value(row, "contractDate"))
            assertEquals(DataFormatter().formatCellValue(sheet.getRow(15).getCell(80)), value(row, "completedWorkPct"))
        }
    }

    @Test fun `allocation reader also retains cents hidden by number formatting`() {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Batch 9 Allocation")
            sheet.createRow(0).apply {
                listOf("Sub-Project ID", "Batch ID", "Total Investment Cost, UAH",
                    "Total Investment Cost, UAH (excl. local co-financing)", "Local co-financing, UAH")
                    .forEachIndexed { index, title -> createCell(index).setCellValue(title) }
            }
            val rounded = workbook.createCellStyle().apply { dataFormat = workbook.createDataFormat().getFormat("#,##0") }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("VN09_01")
                createCell(1).setCellValue(9.0)
                listOf(1234.56, 1000.25, 234.31).forEachIndexed { index, amount ->
                    createCell(index + 2).apply { setCellValue(amount); cellStyle = rounded }
                }
            }
            val row = parse(workbook)
            assertEquals("1234.56", value(row, "fundingTotal"))
            assertEquals("1000.25", value(row, "eibCost"))
            assertEquals("234.31", value(row, "localCost"))
        }
    }

    // The workbook reader is deliberately file-private; exercise it without exposing it as an API.
    private fun row(value: String): Any {
        val type = Class.forName("oms.urp3.service.MonitoringWorkbookSource\$Row")
        val constructor = type.getDeclaredConstructor(String::class.java, String::class.java, Map::class.java,
            String::class.java, String::class.java, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
        return constructor.newInstance("VN08_01", "VN08_01",
            mapOf("fundingTotal" to value, "contractedTotal" to value), "16", "test.xlsx", false)
    }
    private fun number(value: String, key: String = "fundingTotal"): Double = row(value).let {
        it.javaClass.getDeclaredMethod("number", String::class.java).apply { isAccessible = true }.invoke(it, key) as Double
    }

    @Test fun `registry monetary attributes are hryvnia not the manual form default currency`() {
        val row = row("1,234,567.89")
        assertEquals("UAH", row.javaClass.getDeclaredMethod("currency").apply { isAccessible = true }.invoke(row))
    }

    @Test fun `Excel grouped amounts survive the actual registry row parser`() {
        val formatted = DataFormatter(Locale.US).formatRawCellContents(1_234_567.89, 4, "#,##0.00")
        assertEquals("1,234,567.89", formatted)
        assertEquals(1_234_567.89, number(formatted))
        assertEquals(1_234_567.89, number(formatted, "contractedTotal"))
        assertEquals(6_484_316.0, number("6,484,316"))
    }

    @Test fun `decimal comma and spaced amounts remain supported`() {
        listOf("1234567,89", "1 234 567,89", "1\u00a0234\u00a0567,89")
            .forEach { assertEquals(1_234_567.89, number(it)) }
        listOf("0", "", "#VALUE!", "NaN", "Infinity")
            .forEach { assertEquals(0.0, number(it)) }
    }
}
