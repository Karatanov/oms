import java.io.File
import java.math.BigDecimal
import java.util.Locale
import kotlinx.serialization.json.*
import org.apache.poi.ss.usermodel.*

/** Read-only export of cached numeric money and its pre-fix Excel display representation. */
fun main(args: Array<String>) {
    data class Value(val legacy: String, val raw: String?, val cell: String)
    val formatter = DataFormatter(Locale.US)
    fun text(cell: Cell?): String = when {
        cell == null -> ""
        cell.cellType != CellType.FORMULA -> formatter.formatCellValue(cell).trim()
        cell.cachedFormulaResultType == CellType.NUMERIC -> formatter.formatRawCellContents(
            cell.numericCellValue, cell.cellStyle.dataFormat.toInt(), cell.cellStyle.dataFormatString).trim()
        cell.cachedFormulaResultType == CellType.STRING -> cell.stringCellValue.trim()
        else -> ""
    }
    fun value(cell: Cell?): Value {
        val numeric = cell != null && (cell.cellType == CellType.NUMERIC ||
            cell.cellType == CellType.FORMULA && cell.cachedFormulaResultType == CellType.NUMERIC)
        val raw = if (numeric && !DateUtil.isCellDateFormatted(cell))
            BigDecimal.valueOf(cell.numericCellValue).stripTrailingZeros().toPlainString() else null
        return Value(text(cell), raw, cell?.address?.formatAsString().orEmpty())
    }
    fun emit(workbook: String, code: String, fields: Map<String, Value>) {
        println(buildJsonObject {
            put("workbook", workbook); put("code", code.trim().uppercase())
            put("fields", buildJsonObject {
                fields.filterValues { it.raw != null }.forEach { (key, field) -> put(key, buildJsonObject {
                    put("old", field.legacy); put("new", field.raw!!); put("cell", field.cell)
                }) }
            })
        })
    }
    require(args.size == 2) { "Usage: monitoring.xlsx allocation.xlsx" }
    WorkbookFactory.create(File(args[0])).use { workbook ->
        val sheet = workbook.getSheet("DB Table")
        val header = sheet.getRow(11)
        fun column(title: String) = (0 until header.lastCellNum).single { text(header.getCell(it)) == title }
        val codeColumn = column("Sub-Project ID")
        val lotColumn = column("Sub-Project Lot ID")
        val money = mapOf("fundingTotal" to 24, "fundingWorks" to 25, "fundingTs" to 26, "fundingCsc" to 27,
            "eibCost" to 28, "localCost" to 29, "contractedTotal" to 36, "contractedEib" to 37,
            "contractedLocal" to 38, "constructionAmount" to 53, "constructionEib" to 54,
            "constructionLocal" to 55, "paidConstruction" to 60)
        val rows = linkedMapOf<String, MutableList<Map<String, Value>>>()
        (15..sheet.lastRowNum).forEach { index ->
            val row = sheet.getRow(index) ?: return@forEach
            val code = text(row.getCell(codeColumn))
            if (code.isBlank()) return@forEach
            val lot = text(row.getCell(lotColumn)).ifBlank { code }.uppercase()
            rows.getOrPut(lot) { mutableListOf() }.add(money.mapValues { value(row.getCell(it.value)) })
        }
        rows.forEach { (code, versions) -> emit("DB_Monitoring table.xlsx", code, money.keys.associateWith { key ->
            versions.last().getValue(key).takeIf { it.legacy.isNotBlank() } ?: versions.first().getValue(key)
        }) }
    }
    WorkbookFactory.create(File(args[1])).use { workbook ->
        val seen = mutableSetOf<String>()
        workbook.forEach { sheet ->
            if (!sheet.sheetName.contains("allocation", true)) return@forEach
            val headerIndex = (0..minOf(12, sheet.lastRowNum)).firstOrNull { index ->
                sheet.getRow(index)?.let { row ->
                    row.any { text(it) == "Sub-Project ID" } && row.any { text(it) == "Batch ID" }
                } == true
            } ?: return@forEach
            val header = sheet.getRow(headerIndex)
            fun column(title: String) = (0 until header.lastCellNum).firstOrNull { text(header.getCell(it)) == title }
            val codeColumn = column("Sub-Project ID") ?: return@forEach
            val money = mapOf("fundingTotal" to column("Total Investment Cost, UAH"),
                "eibCost" to column("Total Investment Cost, UAH (excl. local co-financing)"),
                "localCost" to column("Local co-financing, UAH"))
            (headerIndex + 1..sheet.lastRowNum).forEach rowLoop@{ index ->
                val row = sheet.getRow(index) ?: return@rowLoop
                val code = text(row.getCell(codeColumn)).uppercase()
                if (!code.matches(Regex("[A-Z]{2}\\d{2}_\\d{2}(?:#.+)?")) || !seen.add(code)) return@rowLoop
                emit("UR III AT&PP Allocation Table.xlsx", code, money.mapValues { (_, col) -> value(col?.let(row::getCell)) })
            }
        }
    }
}
