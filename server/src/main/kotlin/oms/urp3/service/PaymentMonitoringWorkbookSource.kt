package oms.urp3.service

import java.util.Locale
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import com.google.auth.oauth2.ServiceAccountCredentials
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory

/**
 * Reads the approved UR III SP Payment Monitoring workbook without turning its
 * aggregate rows into financial documents. The source contains cumulative
 * monitoring indicators, whereas OMS financial records remain individual acts
 * and payments.
 */
object PaymentMonitoringWorkbookSource {
    private const val SOURCE_FILE_ID = "1DDhqbcsF57uwQYwVAgG5kGZUvIr5_9yyCu2eFsvc5UI"
    private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    data class Item(
        val code: String,
        val totalInvestmentUah: Double?,
        val eibInvestmentUah: Double?,
        val localCoFinancingUah: Double?,
        val paidAndInProgressUah: Double?,
        val paidUah: Double?,
        val remainingUah: Double?,
        val plannedEibSharePct: Double?,
        val factualEibSharePct: Double?,
        val eibShareCompliance: String?
    )

    /**
     * The first release deliberately uses a versioned bundled snapshot. The
     * source sheet is private and its public XLSX URL returns 401 to the OMS
     * server, so a live request would leave the screen empty for users.
     */
    fun bundledSnapshot(): List<Item> {
        val csv = checkNotNull(javaClass.classLoader.getResourceAsStream(RESOURCE_PATH)) {
            "The bundled UR III payment monitoring snapshot is missing."
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCsv(csv)
    }

    /** Parses an administrator-uploaded copy of the approved source workbook. */
    fun parseWorkbook(bytes: ByteArray): List<Item> {
        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { workbook ->
            val sheet = workbook.getSheet(SHEET_NAME)
                ?: throw IllegalArgumentException("The workbook has no '$SHEET_NAME' sheet.")
            val headerIndex = (sheet.firstRowNum..sheet.lastRowNum).firstOrNull { index ->
                workbookHeaders(sheet.getRow(index)).containsKey("id")
            } ?: throw IllegalArgumentException("The workbook has no ID header on '$SHEET_NAME'.")
            val headers = workbookHeaders(sheet.getRow(headerIndex))
            requireHeaders(headers)
            return (headerIndex + 1..sheet.lastRowNum).mapNotNull { index ->
                val row = sheet.getRow(index) ?: return@mapNotNull null
                val code = workbookText(row, headers.getValue("id")).trim()
                if (code.isBlank() || code.equals("TOTAL", true) || code.equals("TOTAL:", true)) return@mapNotNull null
                item(
                    code = code,
                    number = { heading -> headers[heading]?.let { workbookNumber(row, it) } },
                    text = { heading -> headers[heading]?.let { workbookText(row, it).trim().takeIf(String::isNotBlank) } }
                )
            }.filter(::hasIndicator)
        }
    }

    /**
     * Downloads the approved Google workbook using a service account when one
     * is configured. A custom URL stays anonymous, which makes it suitable for
     * a separately hosted approved XLSX snapshot.
     */
    internal fun downloadWorkbook(
        overrideUrl: String? = System.getenv("PAYMENT_MONITORING_SOURCE_URL")?.trim()?.takeIf(String::isNotBlank),
        credentialsPath: String? = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")?.trim()?.takeIf(String::isNotBlank),
        accessToken: String? = null,
        readToken: (String) -> String = ::serviceAccountToken,
        openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }
    ): ByteArray {
        val authenticated = overrideUrl == null && (credentialsPath != null || accessToken != null)
        val url = overrideUrl ?: "https://www.googleapis.com/drive/v3/files/$SOURCE_FILE_ID/export?mimeType=$XLSX_MIME_TYPE"
        try {
            val token = accessToken ?: if (authenticated) readToken(requireNotNull(credentialsPath)) else null
            val connection = openConnection(URI(url))
            try {
                connection.apply {
                    instanceFollowRedirects = !authenticated
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("Accept", XLSX_MIME_TYPE)
                    token?.let { setRequestProperty("Authorization", "Bearer $it") }
                }
                require(connection.responseCode in 200..299) {
                    "Payment-monitoring workbook download failed (HTTP ${connection.responseCode}). Check Google Drive sharing or the source URL."
                }
                require(!connection.contentType.orEmpty().contains("text/html", ignoreCase = true)) {
                    "The payment-monitoring source returned a sign-in page instead of an XLSX workbook."
                }
                return connection.inputStream.use { input ->
                    val bytes = input.readBytes()
                    require(bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
                        "The payment-monitoring source did not return an XLSX workbook."
                    }
                    bytes
                }
            } finally {
                connection.disconnect()
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("The approved payment-monitoring source is unavailable.")
        }
    }

    /** Uses the administrator's temporary Google OAuth token for one refresh. */
    internal fun downloadWithUserToken(accessToken: String): ByteArray =
        downloadWorkbook(accessToken = accessToken)

    internal fun parseCsv(csv: String): List<Item> {
        val rows = csv.lineSequence().filter(String::isNotBlank).map(::csvCells).toList()
        val headerIndex = rows.indexOfFirst { row -> headerMap(row).containsKey("id") }
        require(headerIndex >= 0) { "The payment monitoring snapshot has no ID header." }
        val headers = headerMap(rows[headerIndex])
        requireHeaders(headers)
        return rows.drop(headerIndex + 1).mapNotNull { row ->
            val code = cell(row, headers.getValue("id")).trim()
            if (code.isBlank() || code.equals("TOTAL", true) || code.equals("TOTAL:", true)) return@mapNotNull null
            item(code, { heading -> headers[heading]?.let { number(row, it) } }, { heading -> headers[heading]?.let { cell(row, it).trim().takeIf(String::isNotBlank) } })
        }.filter(::hasIndicator)
    }

    private fun item(code: String, number: (String) -> Double?, text: (String) -> String?) = Item(
        code = code,
        totalInvestmentUah = number("total investment cost, uah"),
        eibInvestmentUah = number("investment cost, uah (excl. local co-financing)"),
        localCoFinancingUah = number("local co-financing, uah"),
        paidAndInProgressUah = number("amount paid + in progress, total uah"),
        paidUah = number("amount paid, total uah"),
        remainingUah = number("remaining payment under sp, uah"),
        plannedEibSharePct = number("eib share planned, %"),
        factualEibSharePct = number("eib share factual, %"),
        eibShareCompliance = text("eib share compliance")
    )

    private fun hasIndicator(item: Item) = item.totalInvestmentUah != null || item.paidAndInProgressUah != null || item.paidUah != null

    private fun requireHeaders(headers: Map<String, Int>) {
        setOf("id", "amount paid, total uah", "remaining payment under sp, uah").forEach { header ->
            require(headers.containsKey(header)) { "The payment monitoring workbook is missing '$header'." }
        }
    }

    private fun headerMap(row: List<String>): Map<String, Int> = buildMap {
        row.forEachIndexed { column, value ->
            value.trim().takeIf(String::isNotBlank)?.let { put(normalize(it), column) }
        }
    }

    private fun cell(row: List<String>, column: Int): String = row.getOrElse(column) { "" }

    private fun number(row: List<String>, column: Int?): Double? = column?.let { index ->
        parseNumber(cell(row, index))
    }

    private fun workbookHeaders(row: Row?): Map<String, Int> = buildMap {
        row ?: return@buildMap
        for (index in row.firstCellNum.coerceAtLeast(0) until row.lastCellNum.coerceAtLeast(0)) {
            workbookText(row, index).trim().takeIf(String::isNotBlank)?.let { put(normalize(it), index) }
        }
    }

    private fun workbookText(row: Row, index: Int): String = row.getCell(index)?.let { cell ->
        when (cell.cellType) {
            CellType.FORMULA -> when (cell.cachedFormulaResultType) {
                CellType.NUMERIC -> DATA_FORMATTER.formatRawCellContents(cell.numericCellValue, cell.cellStyle.dataFormat.toInt(), cell.cellStyle.dataFormatString)
                CellType.STRING -> cell.stringCellValue
                else -> ""
            }
            else -> DATA_FORMATTER.formatCellValue(cell)
        }
    }.orEmpty()

    private fun workbookNumber(row: Row, index: Int): Double? = row.getCell(index)?.let { cell ->
        when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.FORMULA -> if (cell.cachedFormulaResultType == CellType.NUMERIC) cell.numericCellValue else parseNumber(workbookText(row, index))
            else -> parseNumber(workbookText(row, index))
        }
    }

    private fun parseNumber(value: String): Double? {
        val normalized = value.replace("\u00A0", "").replace(" ", "").removeSuffix("%").trim()
        if (normalized.isBlank()) return null
        val decimal = when {
            normalized.contains(',') && normalized.contains('.') -> normalized.replace(",", "")
            normalized.contains(',') -> normalized.replace(',', '.')
            else -> normalized
        }
        return decimal.toDoubleOrNull()
    }

    /** Minimal RFC-4180 compatible reader for the quoted CSV snapshot. */
    private fun csvCells(line: String): List<String> {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val char = line[index]) {
                '"' -> if (quoted && line.getOrNull(index + 1) == '"') { value.append(char); index++ } else quoted = !quoted
                ',' -> if (quoted) value.append(char) else { values += value.toString(); value.clear() }
                else -> value.append(char)
            }
            index++
        }
        values += value.toString()
        return values
    }

    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.US)

    private const val RESOURCE_PATH = "payment-monitoring/ur-iii-sp-payment-monitoring.csv"
    private const val SHEET_NAME = "UR III SP Payment Monitoring"
    private val DATA_FORMATTER = DataFormatter(Locale.US)

    private fun serviceAccountToken(path: String): String = try {
        File(path).inputStream().use {
            ServiceAccountCredentials.fromStream(it)
                .createScoped(listOf("https://www.googleapis.com/auth/drive.readonly"))
                .refreshAccessToken().tokenValue
        }
    } catch (_: Exception) {
        throw IllegalArgumentException("Google Drive authentication failed. Check GOOGLE_APPLICATION_CREDENTIALS.")
    }
}
