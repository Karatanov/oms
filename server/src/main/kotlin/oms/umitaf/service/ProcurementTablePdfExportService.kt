package oms.umitaf.service

import oms.umitaf.domain.ProcurementRecord
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Printable procurement register. The browser submits the selected records in
 * its current order; this service keeps all displayed columns readable by
 * placing the wide register and the contract/finance fields in two A3 tables.
 */
class ProcurementTablePdfExportService(
    private val tableExporter: LandscapeTablePdfExportService = LandscapeTablePdfExportService()
) {
    enum class Language { UK, EN }

    fun export(records: List<ProcurementRecord>, language: Language, filterSummary: String?): ByteArray {
        val labels = Labels(language)
        val overview = section(
            title = labels["overview"],
            columns = listOf(
                "code" to 74f, "tranche" to 36f, "name" to 255f, "promotor" to 130f,
                "method" to 120f, "status" to 130f, "attempts" to 54f, "pig" to 135f, "prozorro" to 150f
            ),
            records = records.map { record -> listOf(
                record.subProjectId, tranche(record.batchId), localized(record.subprojectNameUk, record.subprojectNameEn, language),
                localized(record.fbName, record.promotorName, language), localizedValue(record.procurementMethod, language),
                procurementStatus(record.purchaseStatus, language), record.tenderAttemptCount?.toString().orEmpty(),
                localizedValue(record.pigViolations, language), record.prozorroTenderId.orEmpty()
            ) }, labels = labels, filterSummary = filterSummary
        )
        val contracts = section(
            title = labels["contracts"],
            columns = listOf(
                "code" to 70f, "contract_date" to 66f, "contract_end" to 75f, "actual_end" to 84f,
                "contractor_id" to 86f, "contractor" to 190f, "with_vat" to 92f, "without_vat" to 96f,
                "dream" to 66f, "estimated_eib" to 96f, "local" to 74f, "actual_eib" to 96f,
                "guarantee" to 68f, "contract_link" to 138f
            ),
            records = records.map { record -> listOf(
                record.subProjectId, date(record.contractDate), date(record.contractEndDate), date(record.actualisedContractEndDate),
                record.contractorId.orEmpty(), localized(record.contractorNameUkr, record.contractorNameEng, language),
                number(record.contractAmountUah, language), number(record.contractAmountUahWithoutVat, language), percent(record.dreamCoFinancingPct, language),
                number(record.estimatedEibUah, language), percent(record.realLocalCoFinancingPct, language),
                number(record.realEibFinancingUah, language), percent(record.bankGuarantee, language), record.contractLink.orEmpty()
            ) }, labels = labels, filterSummary = filterSummary
        )
        return merge(listOf(overview, contracts))
    }

    private fun section(
        title: String,
        columns: List<Pair<String, Float>>,
        records: List<List<String>>,
        labels: Labels,
        filterSummary: String?
    ) = tableExporter.export(
        LandscapeTablePdfExportService.Request(
            title = labels["title"] + " — $title",
            totalLabel = labels["total"], filtersLabel = labels["filters"], noRecordsLabel = labels["no_records"],
            exportDateLabel = labels["export_date"], pageLabel = { page, total -> labels.page(page, total) },
            columns = columns.map { LandscapeTablePdfExportService.Column(labels[it.first], it.second) },
            rows = records, filterSummary = filterSummary
        )
    )

    private fun merge(parts: List<ByteArray>): ByteArray = PDDocument().use { destination ->
        // `importPage` keeps references to the source page streams until the
        // destination document is written. Keep all source documents open for
        // the complete merge; closing them earlier produces a corrupt PDF.
        val sources = parts.map(PDDocument::load)
        try {
            sources.forEach { source ->
                source.pages.forEach { page -> destination.importPage(page) }
            }
            ByteArrayOutputStream().use { output ->
                destination.save(output)
                output.toByteArray()
            }
        } finally {
            sources.forEach(PDDocument::close)
        }
    }

    private fun localized(uk: String?, en: String?, language: Language) =
        if (language == Language.EN) en?.takeIf(String::isNotBlank) ?: uk.orEmpty() else uk?.takeIf(String::isNotBlank) ?: en.orEmpty()
    private fun localizedValue(value: String?, language: Language): String {
        val source = value.orEmpty().trim()
        val divider = source.indexOf(" / ")
        if (divider < 0) return source
        return if (language == Language.EN) source.substring(divider + 3).trim().ifBlank { source } else source.substring(0, divider).trim()
    }
    private fun procurementStatus(value: String?, language: Language): String {
        val source = value.orEmpty().trim()
        val statuses = listOf(
            "Не розпочато" to "Not Started", "Закупівля триває" to "Tender Ongoing",
            "Повідомлення про намір укласти договір" to "Contract award notice", "Договір укладено" to "Contract signed",
            "Відмінено" to "Cancelled", "Договір розірвано" to "Contract terminated"
        )
        val status = statuses.firstOrNull { source.equals(it.first, true) || source.equals(it.second, true) || source.equals("${it.first} / ${it.second}", true) }
        return status?.let { if (language == Language.EN) it.second else it.first } ?: localizedValue(source, language)
    }
    private fun tranche(value: Int) = when (value) {
        8, 1 -> "A"
        9, 2 -> "B"
        else -> value.toString()
    }
    private fun date(value: java.time.LocalDate?) = value?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
    private fun number(value: Double?, language: Language): String {
        if (value == null || !value.isFinite()) return ""
        return DecimalFormat("#,##0.00", DecimalFormatSymbols(if (language == Language.UK) Locale("uk", "UA") else Locale.US)).format(value)
    }
    private fun percent(value: Double?, language: Language) = number(value, language).takeIf(String::isNotBlank)?.plus(" %").orEmpty()

    private class Labels(private val language: Language) {
        private val values = mapOf(
            "title" to pair("OMS — Закупівлі", "OMS — Procurement"), "overview" to pair("Реєстр закупівель", "Procurement register"),
            "contracts" to pair("Договори та фінансування", "Contracts and financing"), "total" to pair("Усього записів", "Total records"),
            "filters" to pair("Фільтри", "Filters"), "no_records" to pair("Немає закупівель для експорту", "No procurement records to export"),
            "export_date" to pair("Дата експорту", "Export date"), "code" to pair("Код субпроєкту", "Subproject code"), "tranche" to pair("Транш", "Tranche"),
            "name" to pair("Назва субпроєкту", "Subproject name"), "promotor" to pair("Назва ФБ", "Promotor name"), "method" to pair("Метод закупівлі", "Procurement method"),
            "status" to pair("Статус закупівлі", "Procurement status"), "attempts" to pair("Кількість спроб", "Tender attempts"), "pig" to pair("Порушення PIG", "PIG violations"),
            "prozorro" to pair("Посилання PROZORRO", "PROZORRO reference"), "contract_date" to pair("Дата договору", "Contract date"),
            "contract_end" to pair("Дата завершення договору", "Contract end date"), "actual_end" to pair("Актуалізована дата завершення", "Actualised contract end date"),
            "contractor_id" to pair("ID підрядника", "Contractor ID"), "contractor" to pair("Назва підрядника", "Contractor name"),
            "with_vat" to pair("Сума договору з ПДВ, грн", "Contract amount, UAH with VAT"), "without_vat" to pair("Сума договору без ПДВ, грн", "Contract amount, UAH without VAT"),
            "dream" to pair("Співфінансування DREAM, %", "DREAM co-financing, %"), "estimated_eib" to pair("Оціночне фінансування ЄІБ, грн", "Estimated EIB financing, UAH"),
            "local" to pair("Фактичне місцеве співфінансування, %", "Real local co-financing, %"), "actual_eib" to pair("Фактичне фінансування ЄІБ, грн", "Real EIB financing, UAH"),
            "guarantee" to pair("Банківська гарантія, %", "Bank guarantee, %"), "contract_link" to pair("Посилання на договір", "Contract link")
        )
        private fun pair(uk: String, en: String) = if (language == Language.UK) uk else en
        operator fun get(key: String) = values[key].orEmpty()
        fun page(current: Int, total: Int) = if (language == Language.UK) "Сторінка $current з $total" else "Page $current of $total"
    }
}
