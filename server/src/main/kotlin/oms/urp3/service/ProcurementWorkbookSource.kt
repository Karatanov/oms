package oms.urp3.service

import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.google.auth.oauth2.ServiceAccountCredentials
import java.io.File

/** Reader for the approved DB6.3 Initial Contracts register. Header names, not positions, define the mapping. */
object ProcurementWorkbookSource {
    private const val sourceFileId = "1tN1kbjw94g3zHKLRNDjgryuBXbYWuk2j"
    private const val sheetName = "DB6.3 Initial Contracts"
    // DB6.3 is the fixed, approved tab.  The Sheets export endpoint remains a
    // usable public fallback when a deployment service account was not shared
    // with the Drive file (Drive returns HTTP 404 in that situation).
    private const val approvedSheetGid = "1331483415"

    data class SourceRow(
        val sourceId: String, val subprojectCode: String, val partCode: String?, val batchId: Int, val oblastId: String,
        val sourceContractType: String, val subprojectNameUk: String?, val subprojectNameEn: String?, val promotorName: String?,
        val fbName: String?, val procurementMethod: String?, val purchaseStatus: String?, val tenderAttemptCount: Int?,
        val pigViolations: String?, val prozorroUrl: String?, val contractDate: LocalDate?, val contractEndDate: LocalDate?,
        val actualisedContractEndDate: LocalDate?, val contractorId: String?, val contractorNameEng: String?,
        val contractorNameUkr: String?, val contractReference: String?, val contractAmountUah: Double?, val contractAmountUahWithoutVat: Double?,
        val dreamCoFinancingPct: Double?, val estimatedEibUah: Double?, val realLocalCoFinancingPct: Double?,
        val realEibFinancingUah: Double?, val bankGuarantee: Double?, val contractLink: String?, val comments: String?
    )

    fun downloadAndParse(onProgress: (Int, String) -> Unit = { _, _ -> }): List<SourceRow> {
        onProgress(8, "downloading")
        return parse(download(), onProgress)
    }

    /** Parses an administrator-uploaded copy of the same approved workbook. */
    fun parseApprovedWorkbook(bytes: ByteArray, onProgress: (Int, String) -> Unit = { _, _ -> }): List<SourceRow> =
        parse(bytes, onProgress)

    internal fun parse(bytes: ByteArray, onProgress: (Int, String) -> Unit = { _, _ -> }): List<SourceRow> {
        onProgress(14, "opening_workbook")
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            // DB6.3 is a reporting sheet. Its formula cells contain approved,
            // cached values from the workbook author. Re-evaluating them with
            // Apache POI can shift INDEX/MATCH references and import values
            // from a different source line. Keep the source's cached values.
            val evaluator = workbook.creationHelper.createFormulaEvaluator()
            val sheet = workbook.getSheet(sheetName)
                ?: throw IllegalArgumentException("The approved workbook has no '$sheetName' sheet.")
            onProgress(16, "reading_headers")
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
                cellText(row, 0, evaluator).trim().takeIf(String::isNotBlank)?.let { it to row }
            }
            val valuesByHeader = linkedMapOf<String, List<SourceCellValue>>()
            sourceColumns.forEachIndexed { columnIndex, heading ->
                val column = headers[normalizeHeader(heading)]
                    ?: headerAliases[heading]
                        ?.firstNotNullOfOrNull { alias -> headers[normalizeHeader(alias)] }
                    ?: return@forEachIndexed
                // Reserve a visible part of the operation for every source
                // column so the progress counter advances throughout parsing.
                onProgress(13 + ((columnIndex + 1) * 32 / sourceColumns.size), "column:$heading")
                valuesByHeader[heading] = rows.map { (_, row) -> sourceCellValue(row, column, evaluator) }
            }
            fun value(rowIndex: Int, heading: String): SourceCellValue =
                valuesByHeader[heading]?.get(rowIndex) ?: SourceCellValue()
            val parsedRows = rows.mapIndexed { rowIndex, (sourceId, _) ->
                val projectReference = projectReferenceFromSourceId(sourceId)
                val sourceSubprojectCode = value(rowIndex, "Sub-Project ID").text
                val subprojectCode = projectReference?.subprojectCode ?: sourceSubprojectCode?.substringBefore('#')?.trim()
                    ?: throw IllegalArgumentException("Source row $sourceId has no Sub-Project ID.")
                val partCode = projectReference?.partCode
                    ?: sourceSubprojectCode?.takeIf { it.contains("#LOT", true) }
                // Some approved DB6.3 exports carry an incorrect calculated value in the
                // Type cell. The procurement ID is authoritative in that case: its suffix
                // is one of W, TS, or CSC (for example CH08_09_TS).
                val type = value(rowIndex, "Type").text?.uppercase()
                    ?.takeIf { it in contractTypes }
                    ?: contractTypeFromSourceId(sourceId)
                    ?: throw IllegalArgumentException(
                        "Source row $sourceId has an unsupported Type '${value(rowIndex, "Type").text}'."
                    )
                SourceRow(
                    sourceId, subprojectCode, partCode,
                    value(rowIndex, "Batch ID").number?.toInt() ?: throw IllegalArgumentException("Source row $sourceId has no Batch ID."),
                    value(rowIndex, "Oblast ID").text.orEmpty(), contractTypes.getValue(type),
                    value(rowIndex, "Sub-Project Name (ukr)").text, value(rowIndex, "Sub-Project Name (eng)").text,
                    value(rowIndex, "Promotor Name").text, value(rowIndex, "FB Name").text, value(rowIndex, "Procurement Method").text,
                    value(rowIndex, "Procurement Status").text, value(rowIndex, "Number of attempt for tender procedure").number?.toInt(),
                    value(rowIndex, "PIG violations (if any)").text, value(rowIndex, "Prozorro reference").text,
                    value(rowIndex, "Contract Date").date, value(rowIndex, "Contract end date").date, value(rowIndex, "Actualised contract end date").date,
                    value(rowIndex, "Contractor ID").text, value(rowIndex, "Contractor Name eng").text, value(rowIndex, "Contractor Name ukr").text, value(rowIndex, "Contract Reference No.").text,
                    value(rowIndex, "Contract amount, UAH with VAT").number, value(rowIndex, "Contract amount, UAH without VAT").number,
                    value(rowIndex, "DREAM co-financing (%)").number, value(rowIndex, "Estimated amount and Financing (EIB financing, UAH)").number,
                    value(rowIndex, "Real local co-financing according to contract (%)").number, value(rowIndex, "Real EIB financing according contract data (UAH)").number,
                    value(rowIndex, "Bank guarantee").number, value(rowIndex, "Link to Contract").text,
                    value(rowIndex, "Comments (if any)").text
                )
            }
            require(parsedRows.map { it.sourceId }.toSet().size == parsedRows.size) {
                "The approved workbook contains duplicate source row identifiers."
            }
            return parsedRows
        }
    }

    private fun headers(row: Row?, evaluator: FormulaEvaluator): Map<String, Int> = buildMap {
        row ?: return@buildMap
        for (index in row.firstCellNum.coerceAtLeast(0) until row.lastCellNum.coerceAtLeast(0)) {
            cellText(row, index, evaluator).trim().takeIf(String::isNotBlank)?.let { put(normalizeHeader(it), index) }
        }
    }
    private fun rowHasHeader(row: Row?, heading: String, evaluator: FormulaEvaluator) = headers(row, evaluator).containsKey(normalizeHeader(heading))
    private fun normalizeHeader(value: String): String = value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.US)

    fun downloadWithUserToken(token: String): ByteArray = download(null, "user-oauth", { token })

    internal fun download(
        overrideUrl: String? = System.getenv("PROCUREMENT_SOURCE_URL")?.trim()?.takeIf { it.isNotEmpty() },
        credentialsPath: String? = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.trim()?.takeIf { it.isNotEmpty() },
        readToken: (String) -> String = ::serviceAccountToken,
        openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }
    ): ByteArray {
        // Custom sources never receive Google credentials.
        val authenticated = overrideUrl == null && credentialsPath != null
        val url = overrideUrl ?: if (authenticated) {
            "https://www.googleapis.com/drive/v3/files/$sourceFileId?alt=media&supportsAllDrives=true"
        } else {
            "https://drive.google.com/uc?export=download&id=$sourceFileId"
        }
        val publicExportUrl = "https://docs.google.com/spreadsheets/d/$sourceFileId/export?format=xlsx&gid=$approvedSheetGid"
        try {
            val token = if (authenticated) readToken(requireNotNull(credentialsPath)) else null
            return try {
                downloadFrom(url, token, !authenticated, openConnection)
            } catch (primaryError: IllegalArgumentException) {
                // An explicitly configured source is authoritative and must
                // not be silently replaced.  The built-in source, however,
                // is published as a Google Sheet and can safely fall back to
                // its dedicated XLSX-export URL.
                if (overrideUrl != null) throw primaryError
                try {
                    downloadFrom(publicExportUrl, null, true, openConnection)
                } catch (exportError: IllegalArgumentException) {
                    // A Sheets export that is also denied proves that this is
                    // an access issue, not a malformed workbook.  Tell the
                    // administrator which supported update path can actually
                    // grant access instead of exposing a bare HTTP 404.
                    if (primaryError.message?.contains("HTTP 404") == true &&
                        (exportError.message?.contains("HTTP 401") == true ||
                            exportError.message?.contains("HTTP 403") == true)
                    ) {
                        throw IllegalArgumentException(
                            if (credentialsPath == "user-oauth") {
                                "The connected Google account cannot access the approved procurement workbook. Connect an account that has access, or upload the XLSX manually."
                            } else {
                                "The approved procurement workbook is private. Use ‘Refresh from Google Drive’ with an account that has access, share it with the server service account, or upload the XLSX manually."
                            }
                        )
                    }
                    throw primaryError
                }
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("The approved procurement source is unavailable. Check server connectivity and Google Drive access, or upload the XLSX manually.")
        }
    }

    private fun downloadFrom(
        url: String,
        token: String?,
        followRedirects: Boolean,
        openConnection: (URI) -> HttpURLConnection
    ): ByteArray {
        val connection = openConnection(URI(url))
        try {
            connection.apply {
                instanceFollowRedirects = followRedirects
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            require(connection.responseCode in 200..299) {
                "Workbook download failed (HTTP ${connection.responseCode}). Check Drive API, file sharing and download permissions, or upload the XLSX manually."
            }
            require(!connection.contentType.orEmpty().contains("text/html", true)) {
                "The procurement source returned a sign-in page instead of an XLSX file. Configure service-account access or upload the XLSX manually."
            }
            return connection.inputStream.use { input ->
                val bytes = input.readNBytes(20_000_001)
                require(bytes.size <= 20_000_000) { "The approved procurement workbook must not exceed 20 MB." }
                require(bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
                    "The procurement source did not return an XLSX file. Check the source or upload the XLSX manually."
                }
                bytes
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun serviceAccountToken(path: String): String = try {
        File(path).inputStream().use {
            ServiceAccountCredentials.fromStream(it)
                .createScoped(listOf("https://www.googleapis.com/auth/drive.readonly"))
                .refreshAccessToken().tokenValue
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("Google Drive authentication failed. Check the server service-account key in GOOGLE_APPLICATION_CREDENTIALS.")
    }

    private data class SourceCellValue(val text: String? = null, val number: Double? = null, val date: LocalDate? = null)

    private val formatter = DataFormatter(Locale.US)

    /**
     * Do not recalculate reporting formulas. DB6.3 is distributed with their
     * approved calculated values; POI's evaluator does not reproduce all Excel
     * formula semantics used by the tracker and can return a value from another
     * row. For a formula cell, POI exposes the cached result directly.
     */
    private fun cachedText(cell: Cell): String = when (cell.cachedFormulaResultType) {
        CellType.STRING -> cell.stringCellValue
        CellType.NUMERIC -> formatter.formatRawCellContents(
            cell.numericCellValue, cell.cellStyle.dataFormat.toInt(), cell.cellStyle.dataFormatString
        )
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        else -> ""
    }

    private fun cellText(row: Row?, index: Int, evaluator: FormulaEvaluator): String = row?.getCell(index)
        ?.let { cell -> if (cell.cellType == CellType.FORMULA) cachedText(cell) else formatter.formatCellValue(cell, evaluator) }
        .orEmpty()

    private fun cellNumber(row: Row, index: Int, evaluator: FormulaEvaluator): Double? = row.getCell(index)?.let { cell ->
        when (cell.cellType) {
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue
                else cachedText(cell).replace(',', '.').replace("%", "").trim().toDoubleOrNull()
            CellType.NUMERIC -> cell.numericCellValue
            else -> cellText(row, index, evaluator).replace(',', '.').replace("%", "").trim().toDoubleOrNull()
        }
    }

    private fun cellDate(row: Row, index: Int, evaluator: FormulaEvaluator): LocalDate? = row.getCell(index)?.let { cell ->
        val numeric = when (cell.cellType) {
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else null
            CellType.NUMERIC -> cell.numericCellValue
            else -> null
        }
        if (numeric != null && DateUtil.isCellDateFormatted(cell)) DateUtil.getLocalDateTime(numeric).toLocalDate()
        else parseDate(cellText(row, index, evaluator))
    }

    private fun sourceCellValue(row: Row, index: Int, evaluator: FormulaEvaluator) = SourceCellValue(
        text = cellText(row, index, evaluator).trim().takeIf(String::isNotBlank),
        number = cellNumber(row, index, evaluator),
        date = cellDate(row, index, evaluator)
    )
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

    /** Ordered so the refresh overlay can show the actual column being read. */
    private val sourceColumns = listOf(
        "Procurement ID", "Sub-Project ID", "Batch ID", "Oblast ID", "Type", "Sub-Project Name (ukr)", "Sub-Project Name (eng)",
        "Promotor Name", "FB Name", "Procurement Method", "Procurement Status", "Number of attempt for tender procedure",
        "PIG violations (if any)", "Prozorro reference", "Contract Date", "Contract end date", "Actualised contract end date",
        "Contractor ID", "Contractor Name eng", "Contractor Name ukr", "Contract Reference No.", "Contract amount, UAH with VAT",
        "Contract amount, UAH without VAT", "DREAM co-financing (%)", "Estimated amount and Financing (EIB financing, UAH)",
        "Real local co-financing according to contract (%)", "Real EIB financing according contract data (UAH)", "Bank guarantee", "Link to Contract", "Comments (if any)"
    )
    // The contract URL was added after the first approved template. It stays
    // optional so a historic approved export remains importable.
    private val requiredHeaders = sourceColumns.filterNot { it in setOf("Comments (if any)", "Link to Contract") }.toSet()
    /** Historic approved exports use both Ukrainian and English captions for the contract URL. */
    private val headerAliases = mapOf(
        // DB6.3 currently uses the plural "Link to Contracts". Earlier
        // approved exports use the singular wording, so retain both names.
        "Link to Contract" to listOf("Link to Contracts", "Contract link", "Link to the Contract", "Посилання на договір", "Посилання на контракт")
    )
    private val contractTypes = mapOf("W" to "Роботи / Works", "TS" to "Технічний нагляд / TS", "CSC" to "Інженер-консультант / CSC")
}
