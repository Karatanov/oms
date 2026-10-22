package oms.urp3.service

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import oms.urp3.domain.Project
import oms.urp3.domain.ProjectDocument
import oms.urp3.domain.FinancialRecord
import oms.urp3.domain.FinancialRecordType
import oms.urp3.domain.InspectionReport
import oms.urp3.domain.ProjectMonitoringDetails
import oms.urp3.domain.ProcurementRecord
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.LocalDate
import javax.imageio.ImageIO
import kotlin.math.min

/**
 * A printable, localized subproject card mirroring the General information tab.
 * Vector shapes are used for section icons so the PDF does not depend on a
 * recipient machine having Material Icons installed.
 */
class ProjectPdfExportService {
    enum class Language { UK, EN }

    /** A compact inspection photo supplied by the route after access checks. */
    data class PhotoEvidence(val caption: String, val thumbnail: ByteArray)

    fun export(
        project: Project,
        monitoring: ProjectMonitoringDetails? = null,
        language: Language = Language.UK,
        financialRecords: List<FinancialRecord> = emptyList(),
        inspectionReports: List<InspectionReport> = emptyList(),
        inspectionPhotos: List<PhotoEvidence> = emptyList(),
        procurementRecords: List<ProcurementRecord> = emptyList(),
        documents: List<ProjectDocument> = emptyList()
    ): ByteArray = PDDocument().use { document ->
        val fontBytes = requireNotNull(javaClass.getResourceAsStream("/fonts/DejaVuSans.ttf")) { "PDF export font is unavailable." }
        val font = fontBytes.use { PDType0Font.load(document, it, true) }
        val writer = PdfWriter(document, font, language)
        val financialExecutionBase = (project.subprojectContractAmount ?: project.budgetPlanned).toDouble()
        val paidUah = financialRecords.asSequence()
            .filter { it.recordType == FinancialRecordType.PAYMENT || it.recordType == FinancialRecordType.ADVANCE }
            .mapNotNull { record ->
                when (record.currency.uppercase()) {
                    "UAH" -> record.amount
                    "EUR" -> record.eurExchangeRate?.takeIf { it > 0.0 }?.let { record.amount * it }
                    else -> null
                }
            }
            .sum()
        writer.cover(project.localizedName(monitoring, language), project.siteNumber)
        writer.section(writer.label("basic_information"), Icon.INFO) {
            row(writer.label("name"), project.localizedName(monitoring, language)); row(writer.label("subproject_code"), project.siteName.ifBlank { project.siteNumber })
            row(writer.label("tranche"), if (project.trancheNumber == 2 || project.trancheNumber == 9) "B" else "A"); row(writer.label("status"), writer.status(project.status.name))
            row(writer.label("beneficiary"), monitoring?.beneficiary(language)); row(writer.label("description"), project.description)
        }
        writer.section(writer.label("parameters_and_location"), Icon.LOCATION) {
            row(writer.label("sector"), writer.sector(project.sector)); row(writer.label("construction_type"), writer.constructionType(project.constructionType))
            row(writer.label("address"), project.address); row(writer.label("city"), project.city); row(writer.label("region"), project.region)
            row(writer.label("municipality"), monitoring?.municipality(language)); row(writer.label("coordinates"), project.latitude?.let { latitude -> project.longitude?.let { "$latitude, $it" } })
            row(writer.label("planned_end_date"), project.plannedEndDate ?: project.projectedCompletionTime); row(writer.label("implementation_duration"), project.contractDurationDays?.let(writer::months))
        }
        writer.section(writer.label("section_finance"), Icon.FINANCE) {
            row(writer.label("budget"), money(project.budgetPlanned, project.currency)); row(writer.label("subproject_cost_eib_financing"), project.amounts["eib_financing"]?.let { money(it.amount, it.currency) })
            row(writer.label("subproject_cost_local_financing"), project.amounts["local_financing"]?.let { money(it.amount, it.currency) }); row(writer.label("subproject_contract_amount"), project.subprojectContractAmount?.let { money(it, project.currency) })
            row(writer.label("technical_supervision_contract_amount"), project.technicalSupervisionAmount?.let { money(it, project.currency) }); row(writer.label("engineer_consultant_contract_amount"), project.engineerConsultantContractAmount?.let { money(it, project.currency) })
            if (financialExecutionBase > 0.0) progress(writer.label("financial_execution"), paidUah, financialExecutionBase)
        }
        writer.section(writer.label("construction_contractor_information"), Icon.CONSTRUCTION) {
            row(writer.label("contractor"), project.contractorName); row(writer.label("contract_number"), project.constructionContractNumber); row(writer.label("construction_contract_date"), project.constructionContractSigningDate)
            row(writer.label("construction_start_date"), project.constructionStartDate); row(writer.label("projected_completion_date"), project.projectedCompletionTime ?: project.plannedEndDate); row(writer.label("contract_duration"), project.contractDurationDays?.let(writer::months))
        }
        writer.section(writer.label("technical_supervision_information"), Icon.SUPERVISION) {
            row(writer.label("name"), project.technicalSupervisionName); row(writer.label("contract_number"), project.technicalSupervisionContractNumber); row(writer.label("contract_date"), project.technicalSupervisionContractDate)
            row(writer.label("contract_start_date"), project.technicalSupervisionStartDate); row(writer.label("contract_planned_end_date"), project.technicalSupervisionPlannedEndDate); row(writer.label("contract_duration"), project.technicalSupervisionDurationDays?.let(writer::months))
        }
        writer.section(writer.label("engineer_consultant_information"), Icon.CONSULTANT) {
            row(writer.label("name"), project.engineerConsultantName); row(writer.label("contract_number"), project.engineerConsultantContractNumber); row(writer.label("contract_date"), project.engineerConsultantContractDate)
            row(writer.label("contract_start_date"), project.engineerConsultantStartDate); row(writer.label("contract_planned_end_date"), project.engineerConsultantPlannedEndDate); row(writer.label("contract_duration"), project.engineerConsultantDurationDays?.let(writer::months))
        }
        val hasEngineeringDetails = monitoring?.let { details ->
            listOf(
                details.dreamProjectUrl, details.dedUrl, details.estimateUrl, details.expertiseUrl, details.permitUrl,
                details.constructionActNumber, details.constructionActDate, details.completedWorkPct,
                details.financingUsedPct, details.paidFinancingPct, details.constructionWorkStatus,
                details.constructionWorkSummary, details.constructionWorkComments
            ).any { !it.isNullOrBlank() }
        } == true
        if (hasEngineeringDetails) writer.section(writer.label("engineering_information"), Icon.CONSTRUCTION) {
            monitoring?.let { details ->
                row(writer.label("dream_project"), details.dreamProjectUrl)
                row(writer.label("design_documentation"), details.dedUrl)
                row(writer.label("estimate_documentation"), details.estimateUrl)
                row(writer.label("project_expertise"), details.expertiseUrl)
                row(writer.label("construction_permit"), details.permitUrl)
                row(
                    writer.label("construction_act"),
                    listOfNotNull(details.constructionActNumber, details.constructionActDate)
                        .joinToString(" · ")
                        .takeIf(String::isNotBlank)
                )
                row(writer.label("construction_progress"), details.completedWorkPct?.let { "$it%" })
                row(writer.label("financing_used"), details.financingUsedPct?.let { "$it%" })
                row(writer.label("financing_paid"), details.paidFinancingPct?.let { "$it%" })
                row(writer.label("construction_work_status"), writer.bilingualValue(details.constructionWorkStatus))
                row(writer.label("construction_work_summary"), details.constructionWorkSummary)
                row(writer.label("construction_work_comments"), details.constructionWorkComments)
            }
        }
        val financialAttachmentsByRecordId = documents.asSequence()
            .filter { it.relatedEntity == "financial_record" && it.relatedId != null }
            .groupBy({ it.relatedId!! }, { it.originalName })
        if (financialRecords.isNotEmpty()) writer.section(writer.label("financial_records"), Icon.FINANCE) {
            financialRecords.sortedByDescending { it.recordDate }.forEach { record ->
                row(
                    "${record.recordDate} · ${writer.recordType(record.recordType.name)}",
                    listOfNotNull(
                        record.referenceNumber.takeIf(String::isNotBlank),
                        money(record.amount, record.currency),
                        record.description?.takeIf(String::isNotBlank),
                        financialAttachmentsByRecordId[record.id]
                            ?.takeIf { it.isNotEmpty() }
                            ?.joinToString(", ")
                            ?.let { "${writer.label("attached_files")}: $it" }
                    ).joinToString(" — ")
                )
            }
        }
        if (inspectionReports.isNotEmpty()) writer.section(writer.label("inspection_reports_tab"), Icon.INFO) {
            inspectionReports.sortedByDescending { it.inspectionDate }.forEach { report ->
                row(
                    report.inspectionDate.toString(),
                    listOfNotNull(
                        report.reportCode?.takeIf(String::isNotBlank),
                        writer.status(report.status.name),
                        report.summary?.takeIf(String::isNotBlank)
                    ).joinToString(" — ")
                )
            }
        }
        if (inspectionPhotos.isNotEmpty()) writer.photoEvidenceSection(writer.label("inspection_photos"), inspectionPhotos)
        if (procurementRecords.isNotEmpty()) writer.section(writer.label("procurement"), Icon.CONSTRUCTION) {
            procurementRecords.sortedWith(compareBy<ProcurementRecord> { it.contractDate ?: LocalDate.MIN }.thenBy { it.id }).forEach { record ->
                val identifier = record.subProjectLotId?.takeIf(String::isNotBlank)
                    ?: record.tenderId?.takeIf(String::isNotBlank)
                    ?: record.prozorroTenderId?.takeIf(String::isNotBlank)
                    ?: record.subProjectId
                row(
                    identifier,
                    listOfNotNull(
                        writer.bilingualValue(record.sourceContractType),
                        writer.bilingualValue(record.purchaseStatus),
                        if (language == Language.EN) record.contractorNameEng ?: record.contractorNameUkr else record.contractorNameUkr ?: record.contractorNameEng,
                        record.contractAmountUah?.takeIf { it > 0.0 }?.let { money(it, "UAH") },
                        record.contractDate?.let { "${writer.label("contract_date")}: $it" }
                    ).joinToString(" — ")
                )
            }
        }
        val standaloneDocuments = documents.filterNot { it.relatedEntity == "financial_record" }
        if (standaloneDocuments.isNotEmpty()) writer.section(writer.label("documents"), Icon.INFO) {
            standaloneDocuments.forEach { document ->
                row(writer.documentType(document.docType), document.originalName)
            }
        }
        ByteArrayOutputStream().use { output -> writer.close(); document.save(output); output.toByteArray() }
    }

    private fun Project.localizedName(monitoring: ProjectMonitoringDetails?, language: Language) = if (language == Language.EN) monitoring?.nameEn?.takeIf(String::isNotBlank) ?: name else name
    private fun ProjectMonitoringDetails.beneficiary(language: Language) = if (language == Language.EN) beneficiaryNameEn?.takeIf(String::isNotBlank) ?: beneficiaryNameUk else beneficiaryNameUk
    private fun ProjectMonitoringDetails.municipality(language: Language) = if (language == Language.EN) municipalityNameEn?.takeIf(String::isNotBlank) ?: municipalityNameUk else municipalityNameUk
    private fun money(value: Long, currency: String) = "${DecimalFormat("#,##0.00").format(value.toDouble())} $currency"
    private fun money(value: Double, currency: String) = "${DecimalFormat("#,##0.00").format(value)} $currency"
    private fun money(value: BigDecimal, currency: String) = "${DecimalFormat("#,##0.00").format(value)} $currency"
    private enum class Icon { INFO, LOCATION, FINANCE, CONSTRUCTION, SUPERVISION, CONSULTANT }

    private class PdfWriter(private val document: PDDocument, private val font: PDType0Font, private val language: Language) {
        // Slightly narrower margins and section gaps make the card print more
        // efficiently while keeping the type and fields at their readable size.
        private val margin = 34f; private val width = PDRectangle.A4.width - margin * 2; private var page: PDPage? = null; private var stream: PDPageContentStream? = null; private var y = 0f; private var rowIndex = 0
        private val primary = floatArrayOf(0.153f, 0.553f, 0.678f); private val ink = floatArrayOf(0.10f, 0.17f, 0.22f)
        init { newPage() }
        fun cover(name: String, code: String) { ensure(78f); fillColor(primary); stream!!.addRect(margin, y - 46f, width, 46f); stream!!.fill(); text(name, margin + 15f, y - 21f, 17f, floatArrayOf(1f, 1f, 1f)); text("${label("subproject_code")}: $code", margin + 15f, y - 38f, 9.5f, floatArrayOf(0.92f, 0.98f, 1f)); y -= 60f }
        fun section(title: String, icon: Icon, content: PdfWriter.() -> Unit) { ensure(32f); rowIndex = 0; stream!!.setNonStrokingColor(0.91f, 0.97f, 0.98f); stream!!.addRect(margin, y - 24f, width, 24f); stream!!.fill(); drawIcon(icon, margin + 13f, y - 12f); text(title, margin + 29f, y - 16f, 11.5f, primary); y -= 29f; content(); y -= 8f }
        /** Prints photo evidence as a two-column contact sheet, using saved thumbnails only. */
        fun photoEvidenceSection(title: String, photos: List<PhotoEvidence>) {
            section(title, Icon.INFO) {
                photos.chunked(2).forEach { pair ->
                    val renderable = pair.mapNotNull { evidence ->
                        runCatching { ImageIO.read(ByteArrayInputStream(evidence.thumbnail)) }
                            .getOrNull()
                            ?.let { image -> evidence to image }
                    }
                    if (renderable.isEmpty()) return@forEach

                    val gap = 10f
                    val cellWidth = (width - gap) / 2f
                    val maxImageHeight = 138f
                    val captionHeight = 25f
                    ensure(maxImageHeight + captionHeight + 8f)
                    renderable.forEachIndexed { index, (evidence, image) ->
                        val scale = min(cellWidth / image.width.toFloat(), maxImageHeight / image.height.toFloat())
                        val imageWidth = image.width * scale
                        val imageHeight = image.height * scale
                        val x = margin + index * (cellWidth + gap) + (cellWidth - imageWidth) / 2f
                        val imageY = y - imageHeight
                        stream!!.drawImage(LosslessFactory.createFromImage(document, image), x, imageY, imageWidth, imageHeight)
                        wrap(evidence.caption, 8f, cellWidth)
                            .take(2)
                            .forEachIndexed { lineIndex, line ->
                                text(line, margin + index * (cellWidth + gap), y - maxImageHeight - 10f - lineIndex * 10f, 8f, ink)
                            }
                    }
                    y -= maxImageHeight + captionHeight + 8f
                }
            }
        }
        fun row(label: String, rawValue: Any?) {
            val value = when (rawValue) { null -> return; is LocalDate -> rawValue.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")); else -> rawValue.toString().trim().takeIf(String::isNotEmpty) ?: return }
            val labelWidth = 178f; val valueWidth = width - labelWidth - 20f; val labelLines = wrap(label, 9f, labelWidth - 14f); val valueLines = wrap(value, 9.5f, valueWidth - 14f); val lines = maxOf(labelLines.size, valueLines.size); val height = 12f + lines * 13f
            ensure(height + 4f); if (rowIndex++ % 2 == 0) { stream!!.setNonStrokingColor(0.972f, 0.982f, 0.985f); stream!!.addRect(margin, y - height, width, height); stream!!.fill() }
            stream!!.setStrokingColor(0.83f, 0.88f, 0.90f); stream!!.addRect(margin, y - height, width, height); stream!!.moveTo(margin + labelWidth, y); stream!!.lineTo(margin + labelWidth, y - height); stream!!.stroke()
            labelLines.forEachIndexed { index, line -> text(line, margin + 7f, y - 13f - index * 13f, 9f, floatArrayOf(0.28f, 0.36f, 0.40f)) }; valueLines.forEachIndexed { index, line -> text(line, margin + labelWidth + 7f, y - 13f - index * 13f, 9.5f, ink) }; y -= height
        }
        /** A compact, printable progress bar based on stored UAH payment values. */
        fun progress(label: String, completed: Double, total: Double) {
            val percentage = (completed * 100.0 / total).coerceIn(0.0, 100.0)
            val height = 38f
            ensure(height + 4f)
            stream!!.setStrokingColor(0.83f, 0.88f, 0.90f)
            stream!!.addRect(margin, y - height, width, height)
            stream!!.stroke()
            text(label, margin + 7f, y - 13f, 9f, floatArrayOf(0.28f, 0.36f, 0.40f))
            text("${DecimalFormat("#,##0.00").format(completed)} / ${DecimalFormat("#,##0.00").format(total)} UAH", margin + 158f, y - 13f, 8.5f, ink)
            text("${DecimalFormat("#,##0.0").format(percentage)}%", margin + width - 40f, y - 13f, 9.5f, ink)
            val barX = margin + 7f
            val barY = y - 28f
            val barWidth = width - 14f
            stream!!.setNonStrokingColor(0.91f, 0.94f, 0.95f)
            stream!!.addRect(barX, barY, barWidth, 7f)
            stream!!.fill()
            if (percentage > 0.0) {
                fillColor(primary)
                stream!!.addRect(barX, barY, (barWidth * (percentage / 100.0)).toFloat(), 7f)
                stream!!.fill()
            }
            y -= height
        }
        fun label(key: String) = translations[key]?.get(language) ?: key
        fun months(days: Long): String { val value = kotlin.math.round(days / 30.44).toInt().coerceAtLeast(1); return if (language == Language.UK) "$value міс." else "$value months" }
        fun status(value: String) = when (value.lowercase()) { "planned" -> if (language == Language.UK) "Заплановано" else "Planned"; "active" -> if (language == Language.UK) "Активний" else "Active"; "suspended" -> if (language == Language.UK) "Призупинено" else "Suspended"; "completed" -> if (language == Language.UK) "Завершено" else "Completed"; "archived" -> if (language == Language.UK) "Архівовано" else "Archived"; "dlp" -> "DLP"; else -> value }
        fun recordType(value: String) = when (value.lowercase()) { "invoice" -> if (language == Language.UK) "Рахунок" else "Invoice"; "act" -> if (language == Language.UK) "Акт" else "Act"; "payment" -> if (language == Language.UK) "Платіж" else "Payment"; "advance" -> if (language == Language.UK) "Аванс" else "Advance"; else -> value }
        fun documentType(value: String) = when (value.lowercase()) { "contract" -> if (language == Language.UK) "Договір" else "Contract"; "design" -> if (language == Language.UK) "Проєктна документація" else "Design document"; "estimate" -> if (language == Language.UK) "Кошторис" else "Estimate"; "invoice" -> if (language == Language.UK) "Рахунок" else "Invoice"; "act" -> if (language == Language.UK) "Акт" else "Act"; "photo" -> if (language == Language.UK) "Фото" else "Photo"; else -> if (language == Language.UK) "Інший документ" else "Other document" }
        /** Selects the requested-language part from approved bilingual source values. */
        fun bilingualValue(value: String?): String? {
            val parts = value?.split(Regex("\\s*/\\s*"))?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
            if (parts.isEmpty()) return null
            if (parts.size == 1) return parts.first()
            val ukrainian = parts.firstOrNull { part -> part.any { it in '\u0400'..'\u04FF' } }
            val english = parts.firstOrNull { part -> part.none { it in '\u0400'..'\u04FF' } }
            return if (language == Language.UK) ukrainian ?: parts.first() else english ?: parts.last()
        }
        fun sector(value: String?) = when (value?.lowercase()) { "education" -> if (language == Language.UK) "Освіта" else "Education"; "health", "healthcare" -> if (language == Language.UK) "Охорона здоров’я" else "Healthcare"; "housing" -> if (language == Language.UK) "Житло" else "Housing"; "water_supply" -> if (language == Language.UK) "Водопостачання" else "Water supply"; "sewerage" -> if (language == Language.UK) "Водовідведення" else "Sewerage"; "heat_supply" -> if (language == Language.UK) "Теплопостачання" else "Heat supply"; "shelter" -> if (language == Language.UK) "Укриття" else "Shelter"; null, "" -> null; else -> value.replace('_', ' ').replaceFirstChar(Char::uppercase) }
        fun constructionType(value: String?) = when (value?.lowercase()) { "reconstruction" -> if (language == Language.UK) "Реконструкція" else "Reconstruction"; "capital_repair" -> if (language == Language.UK) "Капітальний ремонт" else "Capital repair"; "new_construction" -> if (language == Language.UK) "Нове будівництво" else "New construction"; null, "" -> null; else -> value.replace('_', ' ').replaceFirstChar(Char::uppercase) }
        fun close() { stream?.close() }
        private fun ensure(height: Float) { if (y - height < margin) newPage() }
        private fun newPage() { stream?.close(); page = PDPage(PDRectangle.A4); document.addPage(page); stream = PDPageContentStream(document, page); y = page!!.mediaBox.height - margin }
        private fun fillColor(color: FloatArray) { stream!!.setNonStrokingColor(color[0], color[1], color[2]) }
        private fun text(value: String, x: Float, baseline: Float, size: Float, color: FloatArray) { stream!!.beginText(); fillColor(color); stream!!.setFont(font, size); stream!!.newLineAtOffset(x, baseline); stream!!.showText(value); stream!!.endText() }
        private fun wrap(value: String, size: Float, limit: Float): List<String> { val words = value.replace('\n', ' ').trim().split(Regex("\\s+")); val result = mutableListOf<String>(); var current = ""; words.forEach { word -> val candidate = if (current.isEmpty()) word else "$current $word"; if (font.getStringWidth(candidate) / 1000f * size <= limit || current.isEmpty()) current = candidate else { result += current; current = word } }; if (current.isNotEmpty()) result += current; return result }
        private fun drawIcon(icon: Icon, x: Float, centerY: Float) { fillColor(primary); when (icon) { Icon.LOCATION -> { stream!!.addRect(x - 4f, centerY - 5f, 8f, 10f); stream!!.fill(); stream!!.setNonStrokingColor(1f,1f,1f); stream!!.addRect(x - 1f, centerY + 1f, 2f, 2f); stream!!.fill() }; Icon.FINANCE -> { stream!!.addRect(x - 5f, centerY - 5f, 3f, 10f); stream!!.addRect(x, centerY - 2f, 3f, 7f); stream!!.addRect(x + 5f, centerY - 7f, 3f, 12f); stream!!.fill() }; Icon.CONSTRUCTION -> { stream!!.addRect(x - 7f, centerY - 2f, 14f, 3f); stream!!.addRect(x - 1.5f, centerY - 7f, 3f, 12f); stream!!.fill() }; else -> { stream!!.addRect(x - 5f, centerY - 5f, 10f, 10f); stream!!.fill() } } }
        companion object {
            private val translations = mapOf(
                "basic_information" to mapOf(Language.UK to "Основна інформація", Language.EN to "Basic information"), "parameters_and_location" to mapOf(Language.UK to "Параметри та розташування", Language.EN to "Parameters and location"), "section_finance" to mapOf(Language.UK to "Фінансування та контракти", Language.EN to "Financing and contracts"), "construction_contractor_information" to mapOf(Language.UK to "Інформація про підрядника будівельних робіт", Language.EN to "Construction contractor information"), "technical_supervision_information" to mapOf(Language.UK to "Інформація про технічний нагляд", Language.EN to "Technical supervision information"), "engineer_consultant_information" to mapOf(Language.UK to "Інформація про інженера-консультанта", Language.EN to "Engineer-consultant information"), "financial_records" to mapOf(Language.UK to "Фінансові записи", Language.EN to "Financial records"), "inspection_reports_tab" to mapOf(Language.UK to "Інспекційні звіти", Language.EN to "Inspection reports"), "inspection_photos" to mapOf(Language.UK to "Фото інспекцій", Language.EN to "Inspection photos"), "procurement" to mapOf(Language.UK to "Закупівлі", Language.EN to "Procurement"), "documents" to mapOf(Language.UK to "Документи", Language.EN to "Documents"),
                "name" to mapOf(Language.UK to "Назва", Language.EN to "Name"), "subproject_code" to mapOf(Language.UK to "Код субпроєкту", Language.EN to "Subproject code"), "tranche" to mapOf(Language.UK to "Транш", Language.EN to "Tranche"), "status" to mapOf(Language.UK to "Статус", Language.EN to "Status"), "beneficiary" to mapOf(Language.UK to "Бенефіціар", Language.EN to "Beneficiary"), "description" to mapOf(Language.UK to "Опис", Language.EN to "Description"), "attached_files" to mapOf(Language.UK to "Прикріплені файли", Language.EN to "Attached files"), "sector" to mapOf(Language.UK to "Сектор", Language.EN to "Sector"), "construction_type" to mapOf(Language.UK to "Тип будівництва", Language.EN to "Construction type"), "address" to mapOf(Language.UK to "Адреса", Language.EN to "Address"), "city" to mapOf(Language.UK to "Населений пункт", Language.EN to "City"), "region" to mapOf(Language.UK to "Область", Language.EN to "Region"), "municipality" to mapOf(Language.UK to "Муніципалітет", Language.EN to "Municipality"), "coordinates" to mapOf(Language.UK to "Координати", Language.EN to "Coordinates"), "planned_end_date" to mapOf(Language.UK to "Планова дата завершення", Language.EN to "Planned completion date"), "implementation_duration" to mapOf(Language.UK to "Строк реалізації", Language.EN to "Implementation duration"),
                "budget" to mapOf(Language.UK to "Загальна вартість проєкту", Language.EN to "Total project cost"), "subproject_cost_eib_financing" to mapOf(Language.UK to "Вартість проєкту, фінансування ЄІБ", Language.EN to "Subproject cost, EIB financing"), "subproject_cost_local_financing" to mapOf(Language.UK to "Вартість проєкту, місцеве фінансування", Language.EN to "Subproject cost, local financing"), "subproject_contract_amount" to mapOf(Language.UK to "Сума контракту субпроєкту", Language.EN to "Subproject contract amount"), "technical_supervision_contract_amount" to mapOf(Language.UK to "Сума договору технічного нагляду", Language.EN to "Technical supervision contract amount"), "engineer_consultant_contract_amount" to mapOf(Language.UK to "Сума договору інженера-консультанта", Language.EN to "Engineer-consultant contract amount"), "financial_execution" to mapOf(Language.UK to "Фінансове виконання", Language.EN to "Financial execution"),
                "contractor" to mapOf(Language.UK to "Підрядник", Language.EN to "Contractor"), "contract_number" to mapOf(Language.UK to "Номер договору", Language.EN to "Contract number"), "contract_start_date" to mapOf(Language.UK to "Дата початку", Language.EN to "Start date"), "contract_planned_end_date" to mapOf(Language.UK to "Планова дата завершення", Language.EN to "Planned completion date"), "construction_contract_date" to mapOf(Language.UK to "Дата договору", Language.EN to "Contract date"), "construction_start_date" to mapOf(Language.UK to "Початок будівництва", Language.EN to "Construction start"), "projected_completion_date" to mapOf(Language.UK to "Планова дата завершення", Language.EN to "Planned completion date"), "contract_duration" to mapOf(Language.UK to "Тривалість контракту", Language.EN to "Contract duration"), "contract_date" to mapOf(Language.UK to "Дата договору", Language.EN to "Contract date"),
                "engineering_information" to mapOf(Language.UK to "Інженерна інформація", Language.EN to "Engineering information"), "dream_project" to mapOf(Language.UK to "Проєкт у DREAM", Language.EN to "Project in DREAM"), "design_documentation" to mapOf(Language.UK to "Проєктна документація в ЄДЕССБ", Language.EN to "Design documentation in EDESSB"), "estimate_documentation" to mapOf(Language.UK to "Кошторисна документація в ЄДЕССБ", Language.EN to "Estimate documentation in EDESSB"), "project_expertise" to mapOf(Language.UK to "Експертиза проєкту в ЄДЕССБ", Language.EN to "Project expertise in EDESSB"), "construction_permit" to mapOf(Language.UK to "Дозвіл на виконання робіт у ЄДЕССБ", Language.EN to "Construction permit in EDESSB"), "construction_act" to mapOf(Language.UK to "Останній акт виконаних робіт", Language.EN to "Latest completed-work act"), "construction_progress" to mapOf(Language.UK to "Частка виконаних будівельних робіт", Language.EN to "Construction work completed"), "financing_used" to mapOf(Language.UK to "Частка використаного фінансування", Language.EN to "Financing used"), "financing_paid" to mapOf(Language.UK to "Частка оплаченого фінансування", Language.EN to "Financing paid"), "construction_work_status" to mapOf(Language.UK to "Поточний статус будівельних робіт", Language.EN to "Current construction work status"), "construction_work_summary" to mapOf(Language.UK to "Резюме виконання будівельних робіт", Language.EN to "Construction work summary"), "construction_work_comments" to mapOf(Language.UK to "Коментарі щодо виконання будівельних робіт", Language.EN to "Construction work comments")
            )
        }
    }
}
