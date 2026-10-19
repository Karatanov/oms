package oms.umitaf.service

import java.util.Locale

/**
 * Reads the approved UR III SP Payment Monitoring workbook without turning its
 * aggregate rows into financial documents. The source contains cumulative
 * monitoring indicators, whereas OMS financial records remain individual acts
 * and payments.
 */
object PaymentMonitoringWorkbookSource {
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

    internal fun parseCsv(csv: String): List<Item> {
        val rows = csv.lineSequence().filter(String::isNotBlank).map(::csvCells).toList()
        val headerIndex = rows.indexOfFirst { row -> headerMap(row).containsKey("id") }
        require(headerIndex >= 0) { "The payment monitoring snapshot has no ID header." }
        val headers = headerMap(rows[headerIndex])
        val required = setOf("id", "amount paid, total uah", "remaining payment under sp, uah")
        required.forEach { header -> require(headers.containsKey(header)) { "The payment monitoring snapshot is missing '$header'." } }
        return rows.drop(headerIndex + 1).mapNotNull { row ->
            val code = cell(row, headers.getValue("id")).trim()
            if (code.isBlank() || code.equals("TOTAL:", true)) return@mapNotNull null
            Item(
                code = code,
                totalInvestmentUah = number(row, headers["total investment cost, uah"]),
                eibInvestmentUah = number(row, headers["investment cost, uah (excl. local co-financing)"]),
                localCoFinancingUah = number(row, headers["local co-financing, uah"]),
                paidAndInProgressUah = number(row, headers["amount paid + in progress, total uah"]),
                paidUah = number(row, headers["amount paid, total uah"]),
                remainingUah = number(row, headers["remaining payment under sp, uah"]),
                plannedEibSharePct = number(row, headers["eib share planned, %"]),
                factualEibSharePct = number(row, headers["eib share factual, %"]),
                eibShareCompliance = headers["eib share compliance"]?.let { cell(row, it).trim().takeIf(String::isNotBlank) }
            )
        }.filter { item ->
            item.totalInvestmentUah != null || item.paidAndInProgressUah != null || item.paidUah != null
        }
    }

    private fun headerMap(row: List<String>): Map<String, Int> = buildMap {
        row.forEachIndexed { column, value ->
            value.trim().takeIf(String::isNotBlank)?.let { put(normalize(it), column) }
        }
    }

    private fun cell(row: List<String>, column: Int): String = row.getOrElse(column) { "" }

    private fun number(row: List<String>, column: Int?): Double? = column?.let { index ->
        cell(row, index).replace("\u00A0", "").replace(" ", "").removeSuffix("%").replace(',', '.')
            .trim().takeIf(String::isNotBlank)?.toDoubleOrNull()
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
}
