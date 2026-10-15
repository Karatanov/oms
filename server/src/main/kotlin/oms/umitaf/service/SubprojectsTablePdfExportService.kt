package oms.umitaf.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import oms.umitaf.domain.Project
import oms.umitaf.domain.ProjectMonitoringDetails
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** Printable A3-landscape export of the current Subprojects registry view. */
class SubprojectsTablePdfExportService {
    enum class Language { UK, EN }

    fun export(
        projects: List<Project>,
        monitoringByProjectId: Map<Long, ProjectMonitoringDetails>,
        language: Language,
        filterSummary: String?
    ): ByteArray = PDDocument().use { document ->
        val fontBytes = requireNotNull(javaClass.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            "PDF export font is unavailable."
        }
        val font = fontBytes.use { PDType0Font.load(document, it, true) }
        val writer = TableWriter(document, font, language, filterSummary)
        writer.write(projects.map { project -> Row(project, monitoringByProjectId[project.id]) })
        writer.addPageNumbers()
        ByteArrayOutputStream().use { output -> document.save(output); output.toByteArray() }
    }

    private data class Row(val project: Project, val monitoring: ProjectMonitoringDetails?)

    private class TableWriter(
        private val document: PDDocument,
        private val font: PDType0Font,
        private val language: Language,
        private val filterSummary: String?
    ) {
        private val pageSize = PDRectangle(1190.55f, 841.89f) // A3 landscape
        private val margin = 28f
        private val usableWidth = pageSize.width - margin * 2
        private val headerHeight = 70f
        private val columns = listOf(
            Column("code", 78f), Column("tranche", 54f), Column("name", 146f), Column("region", 96f),
            Column("city", 78f), Column("sector", 78f), Column("construction_type", 108f), Column("status", 84f),
            Column("budget", 72f), Column("start_date", 72f), Column("planned_end_date", 84f),
            Column("implementation_duration", 90f), Column("contractor", 90f)
        )
        private var page: PDPage? = null
        private var stream: PDPageContentStream? = null
        private var y = 0f
        private var totalRows = 0

        fun write(rows: List<Row>) {
            totalRows = rows.size
            newPage()
            rows.forEach { row ->
                val cells = values(row)
                val height = rowHeight(cells)
                if (y - height < margin + 20f) newPage()
                drawRow(cells, height)
            }
            if (rows.isEmpty()) {
                text(label("no_records"), margin, y - 20f, 10f, INK)
            }
            stream?.close()
        }

        fun addPageNumbers() {
            val total = document.numberOfPages
            document.pages.forEachIndexed { index, currentPage ->
                PDPageContentStream(document, currentPage, AppendMode.APPEND, true).use { footer ->
                    footer.beginText(); footer.setFont(font, 8f); footer.setNonStrokingColor(INK[0], INK[1], INK[2])
                    val value = if (language == Language.UK) "Сторінка ${index + 1} з $total" else "Page ${index + 1} of $total"
                    footer.newLineAtOffset(pageSize.width - margin - textWidth(value, 8f), 15f); footer.showText(value); footer.endText()
                }
            }
        }

        private fun newPage() {
            stream?.close()
            page = PDPage(pageSize).also(document::addPage)
            stream = PDPageContentStream(document, page)
            y = pageSize.height - margin
            drawDocumentHeader()
            drawTableHeader()
        }

        private fun drawDocumentHeader() {
            text(if (language == Language.UK) "OMS — Субпроєкти" else "OMS — Subprojects", margin, y - 15f, 16f, PRIMARY)
            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            val export = if (language == Language.UK) "Дата експорту: $date" else "Export date: $date"
            text(export, pageSize.width - margin - textWidth(export, 8f), y - 13f, 8f, INK)
            val total = if (language == Language.UK) "Усього субпроєктів: $totalRows" else "Total subprojects: $totalRows"
            text(total, margin, y - 30f, 8f, MUTED)
            filterSummary?.takeIf(String::isNotBlank)?.let {
                val prefix = if (language == Language.UK) "Фільтри: " else "Filters: "
                text(prefix + it, margin + 180f, y - 30f, 8f, MUTED)
            }
            y -= headerHeight
        }

        private fun drawTableHeader() {
            val headerTop = y
            stream!!.setNonStrokingColor(PRIMARY[0], PRIMARY[1], PRIMARY[2])
            stream!!.addRect(margin, y - 24f, usableWidth, 24f); stream!!.fill()
            var x = margin
            columns.forEach { column ->
                wrap(label(column.key), 7f, column.width - 6f).take(2).forEachIndexed { line, value ->
                    text(value, x + 3f, headerTop - 9f - line * 8f, 7f, WHITE)
                }
                x += column.width
            }
            y -= 24f
        }

        private fun drawRow(cells: List<String>, height: Float) {
            val top = y
            stream!!.setStrokingColor(0.76f, 0.83f, 0.86f)
            stream!!.addRect(margin, y - height, usableWidth, height)
            var x = margin
            columns.forEachIndexed { index, column ->
                if (index > 0) { stream!!.moveTo(x, y); stream!!.lineTo(x, y - height) }
                val lines = wrap(cells[index], 7f, column.width - 6f)
                lines.forEachIndexed { line, value -> text(value, x + 3f, top - 9f - line * 8.5f, 7f, INK) }
                x += column.width
            }
            stream!!.stroke()
            y -= height
        }

        private fun rowHeight(cells: List<String>): Float =
            (cells.indices.maxOf { index -> wrap(cells[index], 7f, columns[index].width - 6f).size } * 8.5f + 8f).coerceAtLeast(20f)

        private fun values(row: Row): List<String> {
            val project = row.project
            val monitoring = row.monitoring
            val name = if (language == Language.EN) monitoring?.nameEn?.takeIf(String::isNotBlank) ?: project.name else project.name
            return listOf(
                project.siteNumber, if (project.trancheNumber == 2 || project.trancheNumber == 9) "B" else "A", name,
                region(project.region), project.city.orEmpty(), sector(project.sector), construction(project.constructionType), status(project.status.name),
                money(project.budgetPlanned, project.currency), date(project.startDate), date(project.plannedEndDate),
                project.contractDurationDays?.let(::months).orEmpty(), project.contractorName.orEmpty()
            )
        }

        private fun label(key: String): String = labels[key]?.get(language) ?: key
        private fun date(value: LocalDate?): String = value?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
        private fun money(value: Long, currency: String) = "${DecimalFormat("#,##0.00").format(value)} $currency"
        private fun months(days: Long): String { val value = ceil(days / 30.44).toInt().coerceAtLeast(1); return if (language == Language.UK) "$value міс." else "$value months" }
        private fun region(value: String?) = value.orEmpty().replace(Regex("(?i)\\s*(область|oblast|region)$"), "").trim()
        private fun sector(value: String?) = when (value?.lowercase()) { "education" -> if (language == Language.UK) "Освіта" else "Education"; "health", "healthcare" -> if (language == Language.UK) "Охорона здоров’я" else "Healthcare"; "housing" -> if (language == Language.UK) "Житло" else "Housing"; "water_supply" -> if (language == Language.UK) "Водопостачання" else "Water supply"; "sewerage" -> if (language == Language.UK) "Водовідведення" else "Sewerage"; "heat_supply" -> if (language == Language.UK) "Теплопостачання" else "Heat supply"; "shelter" -> if (language == Language.UK) "Укриття" else "Shelter"; else -> value.orEmpty() }
        private fun construction(value: String?) = when (value?.lowercase()) { "reconstruction" -> if (language == Language.UK) "Реконструкція" else "Reconstruction"; "capital_repair" -> if (language == Language.UK) "Капітальний ремонт" else "Capital repair"; "new_construction" -> if (language == Language.UK) "Нове будівництво" else "New construction"; else -> value.orEmpty() }
        private fun status(value: String) = when (value.lowercase()) { "planned" -> if (language == Language.UK) "Заплановано" else "Planned"; "active" -> if (language == Language.UK) "Активний" else "Active"; "suspended" -> if (language == Language.UK) "Призупинено" else "Suspended"; "completed" -> if (language == Language.UK) "Завершено" else "Completed"; "archived" -> if (language == Language.UK) "Архівовано" else "Archived"; else -> value }
        private fun wrap(value: String, size: Float, width: Float): List<String> {
            val result = mutableListOf<String>(); var line = ""
            value.replace('\n', ' ').trim().split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                splitLongWord(word, size, width).forEach { piece ->
                    val candidate = if (line.isEmpty()) piece else "$line $piece"
                    if (line.isNotEmpty() && textWidth(candidate, size) > width) { result += line; line = piece } else line = candidate
                }
            }
            if (line.isNotEmpty()) result += line
            return result.ifEmpty { listOf("—") }
        }
        private fun splitLongWord(word: String, size: Float, width: Float): List<String> {
            if (textWidth(word, size) <= width) return listOf(word)
            val pieces = mutableListOf<String>(); var current = ""
            word.forEach { character ->
                val candidate = current + character
                if (current.isNotEmpty() && textWidth(candidate, size) > width) { pieces += current; current = character.toString() } else current = candidate
            }
            if (current.isNotEmpty()) pieces += current
            return pieces
        }
        private fun textWidth(value: String, size: Float) = font.getStringWidth(value) / 1000f * size
        private fun text(value: String, x: Float, baseline: Float, size: Float, color: FloatArray) { stream!!.beginText(); stream!!.setNonStrokingColor(color[0], color[1], color[2]); stream!!.setFont(font, size); stream!!.newLineAtOffset(x, baseline); stream!!.showText(value); stream!!.endText() }
        private data class Column(val key: String, val width: Float)
        companion object {
            private val PRIMARY = floatArrayOf(.153f, .553f, .678f); private val INK = floatArrayOf(.10f, .17f, .22f); private val MUTED = floatArrayOf(.28f, .36f, .40f); private val WHITE = floatArrayOf(1f, 1f, 1f)
            private val labels = mapOf(
                "code" to mapOf(Language.UK to "Код субпроєкту", Language.EN to "Subproject code"), "tranche" to mapOf(Language.UK to "Транш", Language.EN to "Tranche"), "name" to mapOf(Language.UK to "Назва субпроєкту", Language.EN to "Subproject name"), "region" to mapOf(Language.UK to "Область", Language.EN to "Region"), "city" to mapOf(Language.UK to "Населений пункт", Language.EN to "City"), "sector" to mapOf(Language.UK to "Сектор", Language.EN to "Sector"), "construction_type" to mapOf(Language.UK to "Тип будівництва", Language.EN to "Construction type"), "status" to mapOf(Language.UK to "Статус", Language.EN to "Status"), "budget" to mapOf(Language.UK to "Загальна вартість проєкту", Language.EN to "Total project cost"), "start_date" to mapOf(Language.UK to "Дата початку", Language.EN to "Start date"), "planned_end_date" to mapOf(Language.UK to "Планова дата завершення", Language.EN to "Planned completion"), "implementation_duration" to mapOf(Language.UK to "Строк реалізації", Language.EN to "Implementation duration"), "contractor" to mapOf(Language.UK to "Підрядник", Language.EN to "Contractor"), "no_records" to mapOf(Language.UK to "Немає субпроєктів для експорту", Language.EN to "No subprojects to export")
            )
        }
    }
}
