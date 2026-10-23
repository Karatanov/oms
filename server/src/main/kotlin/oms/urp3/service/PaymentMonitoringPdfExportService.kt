package oms.urp3.service

/** Printable snapshot of the read-only UR III subproject payment-monitoring register. */
class PaymentMonitoringPdfExportService(
    private val tableExporter: LandscapeTablePdfExportService = LandscapeTablePdfExportService()
) {
    enum class Language { UK, EN }

    fun export(items: List<PaymentMonitoringWorkbookSource.Item>, language: Language): ByteArray {
        val labels = Labels(language)
        return tableExporter.export(
            LandscapeTablePdfExportService.Request(
                title = if (language == Language.UK) {
                    "OMS — Моніторинг платежів за субпроєктами"
                } else {
                    "OMS — Subproject payment monitoring"
                },
                totalLabel = if (language == Language.UK) "Усього субпроєктів" else "Total subprojects",
                filtersLabel = if (language == Language.UK) "Порядок" else "Order",
                noRecordsLabel = if (language == Language.UK) "Немає даних для експорту" else "No data to export",
                exportDateLabel = if (language == Language.UK) "Дата експорту" else "Export date",
                pageLabel = { current, total ->
                    if (language == Language.UK) "Сторінка $current з $total" else "Page $current of $total"
                },
                columns = listOf(
                    "code" to 64f, "total" to 83f, "eib" to 83f, "local" to 83f,
                    "progress" to 88f, "paid" to 78f, "remaining" to 80f,
                    "planned" to 60f, "factual" to 60f, "compliance" to 90f
                ).map { LandscapeTablePdfExportService.Column(labels[it.first], it.second) },
                rows = items.map(::values),
                filterSummary = if (language == Language.UK) "За кодом субпроєкту" else "By subproject code"
            )
        )
    }

    private fun values(item: PaymentMonitoringWorkbookSource.Item) = listOf(
        item.code,
        item.totalInvestmentUah.money(),
        item.eibInvestmentUah.money(),
        item.localCoFinancingUah.money(),
        item.paidAndInProgressUah.money(),
        item.paidUah.money(),
        item.remainingUah.money(),
        item.plannedEibSharePct.percent(),
        item.factualEibSharePct.percent(),
        item.eibShareCompliance.cleanStatus()
    )

    private fun Double?.money(): String = this?.let { "%.2f".format(java.util.Locale.US, it) } ?: "—"
    private fun Double?.percent(): String = this?.let { "%.2f%%".format(java.util.Locale.US, it) } ?: "—"
    private fun String?.cleanStatus(): String = this
        ?.replace("✓", "")?.replace("✅", "")?.replace("🔴", "")?.trim()?.ifBlank { null } ?: "—"

    private class Labels(language: Language) {
        private val values = if (language == Language.UK) mapOf(
            "code" to "Код субпроєкту", "total" to "Загальна вартість, грн", "eib" to "ЄІБ, грн",
            "local" to "Місцеве співфінансування, грн", "progress" to "Сплачено і в роботі, грн",
            "paid" to "Сплачено, грн", "remaining" to "Залишилось, грн", "planned" to "План ЄІБ, %",
            "factual" to "Факт ЄІБ, %", "compliance" to "Відповідність частки ЄІБ"
        ) else mapOf(
            "code" to "Subproject code", "total" to "Total cost, UAH", "eib" to "EIB financing, UAH",
            "local" to "Local co-financing, UAH", "progress" to "Paid and in progress, UAH",
            "paid" to "Paid, UAH", "remaining" to "Remaining, UAH", "planned" to "Planned EIB, %",
            "factual" to "Actual EIB, %", "compliance" to "EIB share compliance"
        )
        operator fun get(key: String) = values.getValue(key)
    }
}
