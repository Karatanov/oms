package oms.umitaf.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Approved DB6 workbook reader. It deliberately accepts only the known tracker sheet and columns. */
object ProcurementWorkbookSource {
    private const val sourceFileId = "1tN1kbjw94g3zHKLRNDjgryuBXbYWuk2j"
    private const val sheetName = "DB6 Procurement Tracker"

    data class SourceRow(
        val sourceId: String, val subprojectCode: String, val partCode: String?, val batchId: Int,
        val oblastId: String, val promotorName: String?, val subprojectNameEn: String?, val contractType: String,
        val typeCode: String, val procurementMethod: String?, val estimatedProzorroDate: LocalDate?,
        val estimatedBidSubmissionDate: LocalDate?, val estimatedContractDate: LocalDate?,
        val estimatedContractEndDate: LocalDate?, val estimatedTotalUah: Double?, val estimatedEibUah: Double?,
        val estimatedLocalUah: Double?, val tenderId: String?, val purchaseStatus: String?, val contractDate: LocalDate?,
        val prozorroUrl: String?, val comments: String?, val contractAmountUah: Double?, val contractDurationMonths: Int?
    )

    fun downloadAndParse(): List<SourceRow> = parse(download())

    internal fun parse(bytes: ByteArray): List<SourceRow> {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("The approved workbook has no '$sheetName' sheet.")
            val headerIndex = (sheet.firstRowNum..sheet.lastRowNum).firstOrNull { row ->
                cellText(sheet.getRow(row), 0).equals("Procurement ID", ignoreCase = true)
            } ?: throw IllegalArgumentException("The approved workbook has no Procurement ID header.")
            val rows = (headerIndex + 1..sheet.lastRowNum).mapNotNull { rowNumber ->
                val row = sheet.getRow(rowNumber) ?: return@mapNotNull null
                val sourceId = cellText(row, 0).trim()
                if (sourceId.isBlank()) return@mapNotNull null
                val code = cellText(row, 1).trim()
                val type = cellText(row, 9).trim().uppercase()
                require(code.isNotBlank() && type in contractTypes) { "Invalid source row $sourceId." }
                val part = code.takeIf { it.contains("#LOT", ignoreCase = true) }
                SourceRow(
                    sourceId, code.substringBefore('#'), part, cellNumber(row, 2)?.toInt()
                        ?: throw IllegalArgumentException("Missing batch for $sourceId."), cellText(row, 3).trim(),
                    cellText(row, 4).blankToNull(), cellText(row, 7).blankToNull(), contractTypes.getValue(type), type,
                    normalizeMethod(cellText(row, 10)), cellDate(row, 13), cellDate(row, 14), cellDate(row, 15), cellDate(row, 16),
                    cellNumber(row, 17), cellNumber(row, 18), cellNumber(row, 19), cellText(row, 20).blankToNull(),
                    normalizeStatus(cellText(row, 22)), cellDate(row, 25), cellText(row, 26).blankToNull(),
                    cellText(row, 27).blankToNull(), cellNumber(row, 32), cellNumber(row, 36)?.toInt()
                )
            }
            require(rows.map { it.sourceId }.toSet().size == rows.size) { "The approved workbook contains duplicate Procurement IDs." }
            return rows
        }
    }

    private fun download(): ByteArray {
        val url = System.getenv("PROCUREMENT_SOURCE_URL")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "https://drive.google.com/uc?export=download&id=$sourceFileId"
        try {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            }
            connection.inputStream.use { input ->
                val bytes = input.readBytes()
                val contentType = connection.contentType.orEmpty()
                require(connection.responseCode in 200..299 && bytes.take(2).toByteArray().contentEquals(byteArrayOf(0x50, 0x4b))) {
                    "The approved procurement source is unavailable. Configure PROCUREMENT_SOURCE_URL with a server-readable XLSX download URL."
                }
                require(!contentType.contains("text/html", ignoreCase = true)) { "The procurement source returned a sign-in page instead of an XLSX file." }
                return bytes
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("The approved procurement source is unavailable. Configure PROCUREMENT_SOURCE_URL with a server-readable XLSX download URL.")
        }
    }

    private val formatter = DataFormatter(Locale.US)
    private fun cellText(row: org.apache.poi.ss.usermodel.Row?, index: Int): String = row?.getCell(index)?.let(formatter::formatCellValue).orEmpty()
    private fun cellNumber(row: org.apache.poi.ss.usermodel.Row, index: Int): Double? = row.getCell(index)?.let { cell ->
        when (cell.cellType) { org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
            else -> cellText(row, index).replace(',', '.').toDoubleOrNull() }
    }
    private fun cellDate(row: org.apache.poi.ss.usermodel.Row, index: Int): LocalDate? = row.getCell(index)?.let { cell ->
        if (cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) cell.localDateTimeCellValue.toLocalDate()
        else parseDate(cellText(row, index))
    }
    private fun parseDate(value: String): LocalDate? {
        val normalized = value.trim(); if (normalized.isBlank() || normalized.equals("n/a", true) || normalized.equals("not applicable", true) || normalized.equals("no", true)) return null
        for (format in listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("M/d/uuuu"))) {
            try { return LocalDate.parse(normalized, format) } catch (_: DateTimeParseException) { }
        }; return null
    }
    private fun String.blankToNull() = trim().ifBlank { null }
    private fun normalizeStatus(value: String): String? = when {
        value.contains("not started", true) -> "Не розпочато / Not Started"
        value.contains("tender ongoing", true) -> "Закупівля триває / Tender Ongoing"
        value.contains("award notice", true) -> "Повідомлення про намір укласти договір / Contract award notice"
        value.contains("contract signed", true) -> "Договір укладено / Contract signed"
        value.contains("cancelled", true) -> "Відмінено / Cancelled"
        value.contains("terminated", true) -> "Договір розірвано / Contract terminated"
        else -> value.blankToNull()
    }
    private fun normalizeMethod(value: String): String? = when {
        value.contains("national competitive bidding", true) -> "Національні конкурсні торги / National Competitive Bidding"
        value.contains("local shopping", true) || value.contains("direct contracting", true) -> "Місцевий шопінг/Прямий контракт / Local Shopping or Direct Contracting"
        else -> value.blankToNull()
    }
    private val contractTypes = mapOf("W" to "Роботи / Works", "TS" to "Технічний нагляд / TS", "CSC" to "Інженер-консультант / CSC")
}
