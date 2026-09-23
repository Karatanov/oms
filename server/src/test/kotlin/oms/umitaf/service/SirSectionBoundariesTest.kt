package oms.umitaf.service

import java.lang.reflect.Proxy
import oms.umitaf.repository.InspectionReportFileRepository
import oms.umitaf.repository.InspectionReportRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.util.CellRangeAddress
import kotlin.test.*

class SirSectionBoundariesTest {
    @Test fun `removing compact materials preserves subsequent progress and signature`() {
        val service = InspectionReportFileService(
            unusedRepository(InspectionReportFileRepository::class.java),
            InspectionReportService(unusedRepository(InspectionReportRepository::class.java)))
        for (rows in 2..9) {
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("SIR")
                sheet.createRow(9).createCell(0).setCellValue("PURCHASED MATERIALS")
                sheet.createRow(9 + rows).createCell(0).setCellValue("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS")
                sheet.createRow(10 + rows).createCell(0).setCellValue("Keep this progress comment")
                sheet.createRow(11 + rows).createCell(0).setCellValue("UNDP QUALITY ASSURANCE STAFF")
                service.removePurchasedMaterialsLayout(sheet, 10)
                assertEquals("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS", sheet.getRow(9).getCell(0).stringCellValue)
                assertEquals("Keep this progress comment", sheet.getRow(10).getCell(0).stringCellValue)
                assertEquals("UNDP QUALITY ASSURANCE STAFF", sheet.getRow(11).getCell(0).stringCellValue)
            }
        }
    }

    private fun <T> unusedRepository(type: Class<T>): T = type.cast(Proxy.newProxyInstance(
        type.classLoader, arrayOf(type)
    ) { _, _, _ -> error("Workbook formatting must not access the database") })

    @Test fun `short quality section never clears following progress or materials`() {
        val service = InspectionReportFileService(
            unusedRepository(InspectionReportFileRepository::class.java),
            InspectionReportService(unusedRepository(InspectionReportRepository::class.java)))
        for (dataRows in 0..5) {
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("SIR")
                val headerRow = 10
                val nextRow = headerRow + 1 + dataRows
                sheet.createRow(nextRow - 1).createCell(0)
                    .setCellValue("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS")
                sheet.addMergedRegion(CellRangeAddress(nextRow - 1, nextRow - 1, 0, 8))
                sheet.createRow(nextRow).createCell(0).setCellValue("Progress data")
                sheet.createRow(nextRow + 1).createCell(0).setCellValue("PURCHASED MATERIALS")
                repeat(3) {
                    service.configureQualityAssessmentColumns(sheet, headerRow, nextRow)
                    assertEquals("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS", sheet.getRow(nextRow - 1).getCell(0).stringCellValue)
                    assertEquals("Progress data", sheet.getRow(nextRow).getCell(0).stringCellValue)
                    assertEquals("PURCHASED MATERIALS", sheet.getRow(nextRow + 1).getCell(0).stringCellValue)
                    assertTrue(sheet.mergedRegions.any { it.firstRow == nextRow - 1 && it.lastColumn == 8 })
                }
            }
        }
    }
}
