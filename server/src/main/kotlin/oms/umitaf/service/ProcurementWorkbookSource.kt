package oms.umitaf.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaEvaluator
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
        val contractorNameUkr: String?, val contractReference: String?, val contractAmountUah: Double?, val contractAmountUahWithoutVat: Double?,
        val dreamCoFinancingPct: Double?, val estimatedEibUah: Double?, val realLocalCoFinancingPct: Double?,
        val realEibFinancingUah: Double?, val bankGuarantee: Double?, val comments: String?
    )

    fun downloadAndParse(): List<SourceRow> = parse(download())

    /** Parses an administrator-uploaded copy of the same approved workbook. */
    fun parseApprovedWorkbook(bytes: ByteArray): List<SourceRow> = parse(bytes)

    internal fun parse(bytes: ByteArray): List<SourceRow> {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            // DB6.3 is a reporting sheet: most of its values are formulas
            // looking up DB6.2. DataFormatter alone returns the formula text
            // (for example IFERROR(INDEX(...))) instead of its value.
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("The approved workbook has no '$sheetName' sheet.")
            val headerRowNumber = (sheet.firstRowNum..sheet.lastRowNum).firstOrNull { rowHasHeader(sheet.getRow(it), "Procurement ID", evaluator) }
                ?: throw IllegalArgumentException("The approved workbook has no Procurement ID header on '$sheetName'.")
            val headers = headers(sheet.getRow(headerRowNumber), evaluator)
            requiredHeaders.forEach { heading -> require(headers.containsKey(normalizeHeader(heading))) { "The approved workbook is missing '$heading' on '$sheetName'." } }
            val rows = (headerRowNumber + 1..sheet.lastRowNum).mapNotNull { rowNumber ->
                val row = sheet.getRow(rowNumber) ?: return@mapNotNull null
                // Column A is the unique line identifier (for example
                // KH08_09#Lot1_TS$1). "Procurement ID" is a grouping field
                // and legitimately repeats where one procedure has several
                // contracts or lots, so it must not be used as the row key.
                val sourceId = cellText(row, 0, evaluator).trim().takeIf(String::isNotBlank)
                    ?: text(row, headers, "Procurement ID", evaluator)
                    ?: return@mapNotNull null
                val projectReference = projectReferenceFromSourceId(sourceId)
                val sourceSubprojectCode = text(row, headers, "Sub-Project ID", evaluator)
                val subprojectCode = projectReference?.subprojectCode ?: sourceSubprojectCode?.substringBefore('#')?.trim()
                    ?: throw IllegalArgumentException("Source row $sourceId has no Sub-Project ID.")
                val partCode = projectReference?.partCode
                    ?: sourceSubprojectCode?.takeIf { it.contains("#LOT", true) }
                // Some approved DB6.3 exports carry an incorrect calculated value in the
                // Type cell. The procurement ID is authoritative in that case: its suffix
                // is one of W, TS, or CSC (for example CH08_09_TS).
                val type = text(row, headers, "Type", evaluator)?.uppercase()
                    ?.takeIf { it in contractTypes }
                    ?: contractTypeFromSourceId(sourceId)
                    ?: throw IllegalArgumentException(
                        "Source row $sourceId has an unsupported Type '${text(row, headers, "Type", evaluator)}'."
                    )
                SourceRow(
                    sourceId, subprojectCode, partCode,
                    number(row, headers, "Batch ID", evaluator)?.toInt() ?: throw IllegalArgumentException("Source row $sourceId has no Batch ID."),
                    text(row, headers, "Oblast ID", evaluator).orEmpty(), contractTypes.getValue(type),
                    text(row, headers, "Sub-Project Name (ukr)", evaluator), text(row, headers, "Sub-Project Name (eng)", evaluator),
                    text(row, headers, "Promotor Name", evaluator), text(row, headers, "FB Name", evaluator), text(row, headers, "Procurement Method", evaluator),
                    text(row, headers, "Procurement Status", evaluator), number(row, headers, "Number of attempt for tender procedure", evaluator)?.toInt(),
                    text(row, headers, "PIG violations (if any)", evaluator), text(row, headers, "Prozorro reference", evaluator),
                    date(row, headers, "Contract Date", evaluator), date(row, headers, "Contract end date", evaluator), date(row, headers, "Actualised contract end date", evaluator),
                    text(row, headers, "Contractor ID", evaluator), text(row, headers, "Contractor Name eng", evaluator), text(row, headers, "Contractor Name ukr", evaluator), text(row, headers, "Contract Reference No.", evaluator),
                    number(row, headers, "Contract amount, UAH with VAT", evaluator), number(row, headers, "Contract amount, UAH without VAT", evaluator),
                    number(row, headers, "DREAM co-financing (%)", evaluator), number(row, headers, "Estimated amount and Financing (EIB financing, UAH)", evaluator),
                    number(row, headers, "Real local co-financing according to contract (%)", evaluator), number(row, headers, "Real EIB financing according contract data (UAH)", evaluator),
                    number(row, headers, "Bank guarantee", evaluator), text(row, headers, "Comments (if any)", evaluator)
                )
            }
            require(rows.map { it.sourceId }.toSet().size == rows.size) { "The approved workbook contains duplicate Procurement IDs." }
            return rows
        }
    }

    private fun headers(row: Row?, evaluator: FormulaEvaluator): Map<String, Int> = buildMap {
        row ?: return@buildMap
        for (index in row.firstCellNum.coerceAtLeast(0) until row.lastCellNum.coerceAtLeast(0)) {
            cellText(row, index, evaluator).trim().takeIf(String::isNotBlank)?.let { put(normalizeHeader(it), index) }
        }
    }
    private fun rowHasHeader(row: Row?, heading: String, evaluator: FormulaEvaluator) = headers(row, evaluator).containsKey(normalizeHeader(heading))
    private fun text(row: Row, headers: Map<String, Int>, heading: String, evaluator: FormulaEvaluator): String? = headers[normalizeHeader(heading)]
        ?.let { cellText(row, it, evaluator).trim().takeIf(String::isNotBlank) }
    private fun number(row: Row, headers: Map<String, Int>, heading: String, evaluator: FormulaEvaluator): Double? = headers[normalizeHeader(heading)]?.let { cellNumber(row, it, evaluator) }
    private fun date(row: Row, headers: Map<String, Int>, heading: String, evaluator: FormulaEvaluator): LocalDate? = headers[normalizeHeader(heading)]?.let { cellDate(row, it, evaluator) }
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
    private fun cellText(row: Row?, index: Int, evaluator: FormulaEvaluator): String = row?.getCell(index)
        ?.let { cell -> formatter.formatCellValue(cell, evaluator) }
        .orEmpty()
    private fun cellNumber(row: Row, index: Int, evaluator: FormulaEvaluator): Double? = row.getCell(index)?.let { cell ->
        val evaluated = if (cell.cellType == org.apache.poi.ss.usermodel.CellType.FORMULA) evaluator.evaluate(cell) else null
        if (evaluated?.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC) evaluated.numberValue
        else if (cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC) cell.numericCellValue
        else cellText(row, index, evaluator).replace(',', '.').replace("%", "").trim().toDoubleOrNull()
    }
    private fun cellDate(row: Row, index: Int, evaluator: FormulaEvaluator): LocalDate? = row.getCell(index)?.let { cell ->
        val numeric = when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.FORMULA -> evaluator.evaluate(cell)?.takeIf { it.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC }?.numberValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue
            else -> null
        }
        if (numeric != null && DateUtil.isCellDateFormatted(cell)) DateUtil.getLocalDateTime(numeric).toLocalDate()
        else parseDate(cellText(row, index, evaluator))
    }
    private fun parseDate(value: String): LocalDate? {
        val normalized = value.trim()
        if (normalized.isBlank() || normalized.equals("n/a", true) || normalized.equals("not applicable", true) || normalized.equals("no", true)) return null
        for (format in listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("d.M.uuuu"), DateTimeFormatter.ofPattern("M/d/uuuu"))) {
            try { return LocalDate.parse(normalized, format) } catch (_: DateTimeParseException) { }
        }
        return null
    }

    private fun contractTypeFromSourceId(sourceId: String): String? = sourceId
        .substringBefore('$')
        .substringAfterLast('_')
        .uppercase()
        .takeIf { it in contractTypes }

    private data class ProjectReference(val subprojectCode: String, val partCode: String?)

    private fun projectReferenceFromSourceId(sourceId: String): ProjectReference? {
        val identifier = sourceId.substringBefore('$').trim()
        if (contractTypeFromSourceId(identifier) == null) return null
        val projectReference = identifier.substringBeforeLast('_').trim()
        if (projectReference.isBlank()) return null
        return ProjectReference(
            subprojectCode = projectReference.substringBefore('#').trim(),
            partCode = projectReference.takeIf { it.contains("#LOT", true) }
        )
    }

    private val requiredHeaders = setOf(
        "Procurement ID", "Sub-Project ID", "Batch ID", "Oblast ID", "Type", "Sub-Project Name (ukr)", "Sub-Project Name (eng)",
        "Promotor Name", "FB Name", "Procurement Method", "Procurement Status", "Number of attempt for tender procedure",
        "PIG violations (if any)", "Prozorro reference", "Contract Date", "Contract end date", "Actualised contract end date",
        "Contractor ID", "Contractor Name eng", "Contractor Name ukr", "Contract Reference No.", "Contract amount, UAH with VAT",
        "Contract amount, UAH without VAT", "DREAM co-financing (%)", "Estimated amount and Financing (EIB financing, UAH)",
        "Real local co-financing according to contract (%)", "Real EIB financing according contract data (UAH)", "Bank guarantee"
    )
    private val contractTypes = mapOf("W" to "Роботи / Works", "TS" to "Технічний нагляд / TS", "CSC" to "Інженер-консультант / CSC")
}
