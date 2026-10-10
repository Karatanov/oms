package oms.umitaf.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Reader for the approved DB6.3 Initial Contracts register. Header names, not positions, define the mapping. */
object ProcurementWorkbookSource {
    private const val sourceFileId = "1tN1kbjw94g3zHKLRNDjgryuBXbYWuk2j"
    private const val sheetName = "DB6.3 Initial Contracts"

    data class SourceRow(
        val sourceId: String, val subprojectCode: String, val partCode: String?, val batchId: Int, val oblastId: String,
        val sourceContractType: String, val subprojectNameUk: String?, val subprojectNameEn: String?, val promotorName: String?,
        val fbName: String?, val procurementMethod: String?, val purchaseStatus: String?, val tenderAttemptCount: Int?,
        val pigViolations: String?, val prozorroUrl: String?, val contractDate: LocalDate?, val contractEndDate: LocalDate?,
        val actualisedContractEndDate: LocalDate?, val contractorId: String?, val contractorNameEng: String?,
        val contractorNameUkr: String?, val contractAmountUah: Double?, val contractAmountUahWithoutVat: Double?,
        val dreamCoFinancingPct: Double?, val estimatedEibUah: Double?, val realLocalCoFinancingPct: Double?,
        val realEibFinancingUah: Double?, val bankGuarantee: Double?, val comments: String?
    )

    fun downloadAndParse(): List<SourceRow> = parse(download())

    internal fun parse(bytes: ByteArray): List<SourceRow> {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("The approved workbook has no '$sheetName' sheet.")
            val headerRowNumber = (sheet.firstRowNum..sheet.lastRowNum).firstOrNull { rowHasHeader(sheet.getRow(it), "Procurement ID") }
                ?: throw IllegalArgumentException("The approved workbook has no Procurement ID header on '$sheetName'.")
            val headers = headers(sheet.getRow(headerRowNumber))
            requiredHeaders.forEach { heading -> require(headers.containsKey(normalizeHeader(heading))) { "The approved workbook is missing '$heading' on '$sheetName'." } }
            val rows = (headerRowNumber + 1..sheet.lastRowNum).mapNotNull { rowNumber ->
                val row = sheet.getRow(rowNumber) ?: return@mapNotNull null
                val sourceId = text(row, headers, "Procurement ID") ?: return@mapNotNull null
                val subprojectCode = text(row, headers, "Sub-Project ID")
                    ?: throw IllegalArgumentException("Source row $sourceId has no Sub-Project ID.")
                val type = text(row, headers, "Type")?.uppercase()
                    ?: throw IllegalArgumentException("Source row $sourceId has no Type.")
                require(type in contractTypes) { "Source row $sourceId has an unsupported Type '$type'." }
                SourceRow(
                    sourceId, subprojectCode.substringBefore('#').trim(), subprojectCode.takeIf { it.contains("#LOT", true) },
                    number(row, headers, "Batch ID")?.toInt() ?: throw IllegalArgumentException("Source row $sourceId has no Batch ID."),
                    text(row, headers, "Oblast ID").orEmpty(), contractTypes.getValue(type),
                    text(row, headers, "Sub-Project Name (ukr)"), text(row, headers, "Sub-Project Name (eng)"),
                    text(row, headers, "Promotor Name"), text(row, headers, "FB Name"), text(row, headers, "Procurement Method"),
                    text(row, headers, "Procurement Status"), number(row, headers, "Number of attempt for tender procedure")?.toInt(),
                    text(row, headers, "PIG violations (if any)"), text(row, headers, "Prozorro reference"),
                    date(row, headers, "Contract Date"), date(row, headers, "Contract end date"), date(row, headers, "Actualised contract end date"),
                    text(row, headers, "Contractor ID"), text(row, headers, "Contractor Name eng"), text(row, headers, "Contractor Name ukr"),
                    number(row, headers, "Contract amount, UAH with VAT"), number(row, headers, "Contract amount, UAH without VAT"),
                    number(row, headers, "DREAM co-financing (%)"), number(row, headers, "Estimated amount and Financing (EIB financing, UAH)"),
                    number(row, headers, "Real local co-financing according to contract (%)"), number(row, headers, "Real EIB financing according contract data (UAH)"),
                    number(row, headers, "Bank guarantee"), text(row, headers, "Comments (if any)")
                )
            }
            require(rows.map { it.sourceId }.toSet().size == rows.size) { "The approved workbook contains duplicate Procurement IDs." }
            return rows
        }
    }

    private fun headers(row: Row?): Map<String, Int> = buildMap {
        row ?: return@buildMap
        for (index in row.firstCellNum.coerceAtLeast(0) until row.lastCellNum.coerceAtLeast(0)) {
            cellText(row, index).trim().takeIf(String::isNotBlank)?.let { put(normalizeHeader(it), index) }
        }
    }
    private fun rowHasHeader(row: Row?, heading: String) = headers(row).containsKey(normalizeHeader(heading))
    private fun text(row: Row, headers: Map<String, Int>, heading: String): String? = headers[normalizeHeader(heading)]
        ?.let { cellText(row, it).trim().takeIf(String::isNotBlank) }
    private fun number(row: Row, headers: Map<String, Int>, heading: String): Double? = headers[normalizeHeader(heading)]?.let { cellNumber(row, it) }
    private fun date(row: Row, headers: Map<String, Int>, heading: String): LocalDate? = headers[normalizeHeader(heading)]?.let { cellDate(row, it) }
    private fun normalizeHeader(value: String): String = value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.US)

    private fun download(): ByteArray {
        val url = System.getenv("PROCUREMENT_SOURCE_URL")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "https://drive.google.com/uc?export=download&id=$sourceFileId"
        try {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 60_000
                setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            }
            connection.inputStream.use { input ->
                val bytes = input.readBytes(); val contentType = connection.contentType.orEmpty()
                require(connection.responseCode in 200..299 && bytes.take(2).toByteArray().contentEquals(byteArrayOf(0x50, 0x4b))) {
                    "The approved procurement source is unavailable. Configure PROCUREMENT_SOURCE_URL with a server-readable XLSX download URL."
                }
                require(!contentType.contains("text/html", true)) { "The procurement source returned a sign-in page instead of an XLSX file." }
                return bytes
            }
        } catch (error: IllegalArgumentException) { throw error
        } catch (_: Exception) { throw IllegalArgumentException("The approved procurement source is unavailable. Configure PROCUREMENT_SOURCE_URL with a server-readable XLSX download URL.") }
    }

    private val formatter = DataFormatter(Locale.US)
    private fun cellText(row: Row?, index: Int): String = row?.getCell(index)?.let(formatter::formatCellValue).orEmpty()
    private fun cellNumber(row: Row, index: Int): Double? = row.getCell(index)?.let { cell ->
        if (cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC) cell.numericCellValue
        else cellText(row, index).replace(',', '.').replace("%", "").trim().toDoubleOrNull()
    }
    private fun cellDate(row: Row, index: Int): LocalDate? = row.getCell(index)?.let { cell ->
        if (cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) cell.localDateTimeCellValue.toLocalDate()
        else parseDate(cellText(row, index))
    }
    private fun parseDate(value: String): LocalDate? {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.equals("n/a", true) || normalized.equals("not applicable", true) || normalized.equals("no", true)) return null
        for (format in listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("M/d/uuuu"))) {
            try { return LocalDate.parse(normalized, format) } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private val requiredHeaders = setOf(
        "Procurement ID", "Sub-Project ID", "Batch ID", "Oblast ID", "Type", "Sub-Project Name (ukr)", "Sub-Project Name (eng)",
        "Promotor Name", "FB Name", "Procurement Method", "Procurement Status", "Number of attempt for tender procedure",
        "PIG violations (if any)", "Prozorro reference", "Contract Date", "Contract end date", "Actualised contract end date",
        "Contractor ID", "Contractor Name eng", "Contractor Name ukr", "Contract amount, UAH with VAT",
        "Contract amount, UAH without VAT", "DREAM co-financing (%)", "Estimated amount and Financing (EIB financing, UAH)",
        "Real local co-financing according to contract (%)", "Real EIB financing according contract data (UAH)", "Bank guarantee"
    )
    private val contractTypes = mapOf("W" to "Роботи / Works", "TS" to "Технічний нагляд / TS", "CSC" to "Інженер-консультант / CSC")
}
