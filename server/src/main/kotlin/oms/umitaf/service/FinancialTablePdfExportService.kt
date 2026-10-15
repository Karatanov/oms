package oms.umitaf.service

import oms.umitaf.domain.FinancialRecord
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** PDF export of the current Financial Monitoring registry view. */
class FinancialTablePdfExportService(
    private val tableExporter: LandscapeTablePdfExportService = LandscapeTablePdfExportService()
) {
    enum class Language { UK, EN }

    data class Row(
        val record: FinancialRecord,
        val trancheNumber: Int,
        val subprojectUuid: String?,
        val subprojectName: String,
        val subprojectCode: String?,
        val subprojectPartName: String?,
        val region: String
    )

    fun export(rows: List<Row>, language: Language, currency: String, filterSummary: String?): ByteArray {
        val labels = Labels(language)
        val normalizedCurrency = currency.uppercase().takeIf { it in setOf("EUR", "UAH") } ?: "EUR"
        val columns = listOf(
            "type" to 70f, "purpose" to 120f, "tranche" to 45f, "subproject" to 145f, "code" to 85f,
            "part" to 110f, "date" to 75f, "amount" to 85f, "currency" to 45f, "description" to 155f, "author" to 70f
        ).map { LandscapeTablePdfExportService.Column(labels[it.first], it.second) }
        return tableExporter.export(
            LandscapeTablePdfExportService.Request(
                title = if (language == Language.UK) "OMS — Фінансовий моніторинг" else "OMS — Financial Monitoring",
                totalLabel = if (language == Language.UK) "Усього записів" else "Total records",
                filtersLabel = if (language == Language.UK) "Фільтри" else "Filters",
                noRecordsLabel = labels["no_records"],
                exportDateLabel = if (language == Language.UK) "Дата експорту" else "Export date",
                pageLabel = { current, total -> if (language == Language.UK) "Сторінка $current з $total" else "Page $current of $total" },
                columns = columns,
                rows = rows.map { row -> values(row, normalizedCurrency, language) },
                filterSummary = filterSummary
            )
        )
    }

    private fun values(row: Row, currency: String, language: Language): List<String> {
        val record = row.record
        return listOf(
            type(record.recordType.name.lowercase(), language),
            if (record.recordType.name.lowercase() in setOf("payment", "advance")) purpose(record.paymentPurpose, language) else "—",
            if (row.trancheNumber == 2 || row.trancheNumber == 9) "B" else "A",
            row.subprojectName,
            row.subprojectCode.orEmpty(),
            row.subprojectPartName.orEmpty(),
            record.recordDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            displayAmount(record, currency)?.let { money(it, language) } ?: "—",
            currency,
            record.description ?: record.milestone ?: "—",
            "—" // The current web table has no persisted author value.
        )
    }

    private fun displayAmount(record: FinancialRecord, target: String): Long? {
        if (!record.amount.isFinite()) return null
        if (record.currency.equals(target, ignoreCase = true)) return (record.amount * 100).roundToLong()
        if (target == "EUR") return record.amountEurCents
        val rate = record.eurExchangeRate?.takeIf { it.isFinite() && it > 0 && (record.currency.equals("UAH", true) || it != 1.0) }
            ?: return null
        return if (record.currency.equals("EUR", true)) (record.amount * 100 * rate).roundToLong() else null
    }

    private fun money(cents: Long, language: Language): String {
        val locale = if (language == Language.UK) Locale("uk", "UA") else Locale.US
        return DecimalFormat("#,##0.00", DecimalFormatSymbols(locale)).format(cents / 100.0)
    }

    private fun type(value: String, language: Language) = when (value) {
        "invoice" -> if (language == Language.UK) "Рахунок" else "Invoice"
        "act" -> if (language == Language.UK) "Акт" else "Act"
        "payment" -> if (language == Language.UK) "Платіж" else "Payment"
        "advance" -> if (language == Language.UK) "Аванс" else "Advance"
        else -> value
    }

    private fun purpose(value: String, language: Language) = when (value) {
        "equipment" -> if (language == Language.UK) "Оплата за обладнання" else "Equipment payment"
        "technical_supervision" -> if (language == Language.UK) "Оплата технічного нагляду" else "Technical supervision payment"
        "engineer_consultant" -> if (language == Language.UK) "Оплата інженера-консультанта" else "Engineer-consultant payment"
        else -> if (language == Language.UK) "Оплата за будівельним контрактом" else "Construction contract payment"
    }

    private class Labels(language: Language) {
        private val values = mapOf(
            "type" to pair("Тип", "Type"), "purpose" to pair("Призначення", "Purpose"), "tranche" to pair("Транш", "Tranche"), "subproject" to pair("Субпроєкт", "Subproject"), "code" to pair("Код субпроєкту", "Subproject code"), "part" to pair("Частина субпроєкту", "Subproject part"), "date" to pair("Дата", "Date"), "amount" to pair("Сума", "Amount"), "currency" to pair("Валюта", "Currency"), "description" to pair("Опис", "Description"), "author" to pair("Автор", "Author"), "no_records" to pair("Немає фінансових записів для експорту", "No financial records to export")
        )
        private fun pair(uk: String, en: String) = if (language == Language.UK) uk else en
        operator fun get(key: String) = values[key].orEmpty()
    }
}
