package oms.umitaf.service

import oms.umitaf.domain.Project
import oms.umitaf.domain.ProjectMonitoringDetails
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Printable, filtered Subprojects registry built on the shared table renderer. */
class SubprojectsTablePdfExportService(
    private val tableExporter: LandscapeTablePdfExportService = LandscapeTablePdfExportService()
) {
    enum class Language { UK, EN }

    fun export(
        projects: List<Project>,
        monitoringByProjectId: Map<Long, ProjectMonitoringDetails>,
        language: Language,
        filterSummary: String?
    ): ByteArray {
        val labels = Labels(language)
        val budgetCurrencies = projects.map { it.currency.uppercase() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        val budgetHeader = labels["budget"] + budgetCurrencies
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " / ", prefix = " (", postfix = ")")
            .orEmpty()
        // Use compact widths for values which are typically short, reserving
        // the largest printable area for the subproject name.
        val columns = listOf(
            "code" to 60f, "tranche" to 34f, "name" to 270f, "region" to 70f, "city" to 70f,
            "sector" to 65f, "construction_type" to 92f, "status" to 70f, "budget" to 90f,
            "start_date" to 60f, "planned_end_date" to 76f, "implementation_duration" to 70f, "contractor" to 100f
        ).map { (key, width) ->
            LandscapeTablePdfExportService.Column(if (key == "budget") budgetHeader else labels[key], width)
        }
        val rows = projects.map { project ->
            val monitoring = monitoringByProjectId[project.id]
            val name = if (language == Language.EN) monitoring?.nameEn?.takeIf(String::isNotBlank) ?: project.name else project.name
            listOf(
                project.siteNumber, if (project.trancheNumber == 2 || project.trancheNumber == 9) "B" else "A", name,
                region(project.region), project.city.orEmpty(), sector(project.sector, language), construction(project.constructionType, language),
                status(project.status.name, language), money(project.budgetPlanned), date(project.startDate), date(project.plannedEndDate),
                project.contractDurationDays?.let { months(it, language) }.orEmpty(), project.contractorName.orEmpty()
            )
        }
        return tableExporter.export(
            LandscapeTablePdfExportService.Request(
                title = if (language == Language.UK) "OMS — Субпроєкти" else "OMS — Subprojects",
                totalLabel = if (language == Language.UK) "Усього субпроєктів" else "Total subprojects",
                filtersLabel = if (language == Language.UK) "Фільтри" else "Filters",
                noRecordsLabel = labels["no_records"],
                exportDateLabel = if (language == Language.UK) "Дата експорту" else "Export date",
                pageLabel = { current, total -> if (language == Language.UK) "Сторінка $current з $total" else "Page $current of $total" },
                columns = columns,
                rows = rows,
                filterSummary = filterSummary
            )
        )
    }

    private fun date(value: LocalDate?) = value?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
    private fun money(value: Long) = DecimalFormat("#,##0.00").format(value)
    private fun months(days: Long, language: Language): String { val value = ceil(days / 30.44).toInt().coerceAtLeast(1); return if (language == Language.UK) "$value міс." else "$value months" }
    private fun region(value: String?) = value.orEmpty().replace(Regex("(?i)\\s*(область|oblast|region)$"), "").trim()
    private fun sector(value: String?, language: Language) = when (value?.lowercase()) { "education" -> if (language == Language.UK) "Освіта" else "Education"; "health", "healthcare" -> if (language == Language.UK) "Охорона здоров’я" else "Healthcare"; "housing" -> if (language == Language.UK) "Житло" else "Housing"; "water_supply" -> if (language == Language.UK) "Водопостачання" else "Water supply"; "sewerage" -> if (language == Language.UK) "Водовідведення" else "Sewerage"; "heat_supply" -> if (language == Language.UK) "Теплопостачання" else "Heat supply"; "shelter" -> if (language == Language.UK) "Укриття" else "Shelter"; else -> value.orEmpty() }
    private fun construction(value: String?, language: Language) = when (value?.lowercase()) { "reconstruction" -> if (language == Language.UK) "Реконструкція" else "Reconstruction"; "capital_repair" -> if (language == Language.UK) "Капітальний ремонт" else "Capital repair"; "new_construction" -> if (language == Language.UK) "Нове будівництво" else "New construction"; else -> value.orEmpty() }
    private fun status(value: String, language: Language) = when (value.lowercase()) { "planned" -> if (language == Language.UK) "Заплановано" else "Planned"; "active" -> if (language == Language.UK) "Активний" else "Active"; "suspended" -> if (language == Language.UK) "Призупинено" else "Suspended"; "completed" -> if (language == Language.UK) "Завершено" else "Completed"; "archived" -> if (language == Language.UK) "Архівовано" else "Archived"; else -> value }

    private class Labels(private val language: Language) {
        private val values = mapOf(
            "code" to pair("Код субпроєкту", "Subproject code"), "tranche" to pair("Транш", "Tranche"), "name" to pair("Назва субпроєкту", "Subproject name"), "region" to pair("Область", "Region"), "city" to pair("Населений пункт", "City"), "sector" to pair("Сектор", "Sector"), "construction_type" to pair("Тип будівництва", "Construction type"), "status" to pair("Статус", "Status"), "budget" to pair("Загальна вартість проєкту", "Total project cost"), "start_date" to pair("Дата початку", "Start date"), "planned_end_date" to pair("Планова дата завершення", "Planned completion"), "implementation_duration" to pair("Строк реалізації", "Implementation duration"), "contractor" to pair("Підрядник", "Contractor"), "no_records" to pair("Немає субпроєктів для експорту", "No subprojects to export")
        )
        private fun pair(uk: String, en: String) = if (language == Language.UK) uk else en
        operator fun get(key: String) = values[key].orEmpty()
    }
}
