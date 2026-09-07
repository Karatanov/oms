package oms.ufsi.service

import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportFile
import oms.ufsi.domain.Project
import oms.ufsi.repository.InspectionReportFileRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import oms.ufsi.dto.CreateManualInspectionReportRequest
import oms.ufsi.storage.DurableFileStorage
import oms.ufsi.storage.uploadDirectory

data class HealthSafetyObservation(
    val observation: String,
    val answer: String?,
    val comment: String?
)

class InspectionReportFileService(
    private val fileRepository: InspectionReportFileRepository,
    private val reportService: InspectionReportService
) {
    fun createManual(project: Project, request: CreateManualInspectionReportRequest, createdBy: Long): InspectionReport {
        val qaStaff = request.qaStaff?.trim()?.takeIf { it.isNotBlank() } ?: request.inspectorName.trim()
        val report = reportService.createReport(
            project.id, request.inspectionDate, "Manual SIR [${request.inspectionType}]: ${request.contractor}", createdBy,
            inspectionType = request.inspectionType,
            latitude = request.latitude,
            longitude = request.longitude
        )
        val fileName = "SIR-USIF_${request.inspectionDate.replace("-", "")}.xlsx"
        val directory = uploadDirectory("inspection-reports")
        Files.createDirectories(directory)
        val target = directory.resolve("${report.uuid}.xlsx")
        loadManualSirTemplate().use { workbook ->
            populateManualSir(workbook, project, request, qaStaff)
            Files.newOutputStream(target).use(workbook::write)
        }
        try {
            DurableFileStorage.persist(target)
            fileRepository.create(InspectionReportFile(report.id, fileName, target.toString(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Files.size(target)))
            return report
        } catch (exception: Exception) {
            runCatching { DurableFileStorage.delete(target.toString()) }
            throw exception
        }
    }

    /**
     * The manual report is filled into the approved SIR layout, not rebuilt
     * from generic rows.  This preserves the exact column geometry, merged
     * cells, fonts, borders and print settings used by the field template.
     */
    private fun loadManualSirTemplate(): XSSFWorkbook {
        val input = checkNotNull(javaClass.getResourceAsStream("/templates/sir-manual-template.xlsx")) {
            "Manual SIR template is unavailable."
        }
        return input.use { source -> WorkbookFactory.create(source) as? XSSFWorkbook
            ?: error("Manual SIR template must be an XLSX workbook.") }
    }

    private fun populateManualSir(
        workbook: XSSFWorkbook,
        project: Project,
        request: CreateManualInspectionReportRequest,
        qaStaff: String
    ) {
        // The supplied template's second sheet contains photographs from its
        // reference inspection. Generated reports must never carry those
        // unrelated photographs into a new report.
        while (workbook.numberOfSheets > 1) workbook.removeSheetAt(1)
        val sheet = checkNotNull(workbook.getSheet("SIR")) { "Manual SIR sheet is unavailable." }
        val date = LocalDate.parse(request.inspectionDate)

        fun text(row: Int, column: Int, value: String?) {
            sheet.getRow(row - 1).getCell(column - 1).setCellValue(value.orEmpty())
        }
        fun date(row: Int, column: Int) {
            sheet.getRow(row - 1).getCell(column - 1).setCellValue(java.sql.Date.valueOf(date))
        }
        fun clear(rows: IntRange) {
            rows.forEach { row ->
                sheet.getRow(row - 1)?.forEach { cell -> cell.setBlank() }
            }
        }
        fun mergedText(items: List<String>, capacity: Int): List<String> = when {
            items.size <= capacity -> items
            else -> items.take(capacity - 1) + items.drop(capacity - 1).joinToString("\n\n")
        }

        // Clear only cells that hold inspection-specific data. Section labels,
        // merged ranges, borders, row heights and print geometry remain intact.
        clear(13..26)
        clear(28..39)
        clear(41..46)
        clear(49..53)
        text(5, 1, request.contractor)
        text(5, 5, listOf(project.siteNumber, project.address ?: project.name).filter(String::isNotBlank).joinToString(", "))
        date(5, 9)
        text(7, 1, request.contractorRepresentative)
        text(7, 5, qaStaff)
        text(7, 9, request.usifRepresentative)
        text(10, 1, request.skilledLabor)
        text(10, 3, request.unskilledLabor)
        text(10, 5, request.siteManagement)
        text(10, 9, request.weather)

        request.activities.take(14).forEachIndexed { index, activity ->
            val row = 13 + index
            text(row, 1, activity.location)
            text(row, 3, activity.description)
            text(row, 8, activity.onSchedule)
            text(row, 10, activity.remarks)
        }
        mergedText(request.ongoingObservations.map(String::trim).filter(String::isNotBlank), 12)
            .forEachIndexed { index, observation -> text(28 + index, 1, observation) }
        mergedText(request.hseObservations.map { observation ->
            listOf(observation.observation, observation.answer, observation.comment)
                .filterNotNull().map(String::trim).filter(String::isNotBlank).joinToString(" — ")
        }.filter(String::isNotBlank), 6).forEachIndexed { index, observation -> text(41 + index, 1, observation) }
        val qualityRemarks = if (request.qualityRemarks.size <= 5) request.qualityRemarks else {
            request.qualityRemarks.take(4) + oms.ufsi.dto.ManualRemark(
                request.qualityRemarks.drop(4).joinToString("\n\n") { it.comment },
                request.qualityRemarks.drop(4).mapNotNull { it.rectification?.trim()?.takeIf(String::isNotBlank) }
                    .joinToString("\n\n").ifBlank { null }
            )
        }
        qualityRemarks.forEachIndexed { index, remark ->
            text(49 + index, 1, remark.comment)
            text(49 + index, 10, remark.rectification)
        }
        text(55, 1, request.progressComment)
        text(55, 10, request.scheduleRemark)
        text(59, 1, request.inspectorName)
        text(59, 4, request.inspectorTitle)
        date(59, 6)
    }
    fun import(projectId: Long, originalName: String, contentType: String?, input: java.io.InputStream, createdBy: Long): InspectionReport {
        val extension = originalName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("xls", "xlsx")) { "Only XLS or XLSX inspection reports are supported." }
        val date = extractDate(originalName)
        val report = reportService.createReport(projectId, date.toString(), "Imported SIR: $originalName", createdBy)
        val completed = reportService.submitReport(report.uuid.toString())?.let {
            reportService.reviewReport(it.uuid.toString(), "approve", null)
        } ?: error("Could not finalize imported inspection report.")
        val directory = uploadDirectory("inspection-reports")
        Files.createDirectories(directory)
        val target = directory.resolve("${completed.uuid}.$extension")
        try {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            val size = Files.size(target)
            require(size in 1..20_000_000) { "File size must not exceed 20 MB." }
            val detectedContentType = contentType ?: when (extension) {
                "xls" -> "application/vnd.ms-excel"
                else -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
            DurableFileStorage.persist(target)
            fileRepository.create(InspectionReportFile(completed.id, originalName, target.toString(), detectedContentType, size))
            return completed
        } catch (exception: Exception) {
            runCatching { DurableFileStorage.delete(target.toString()) }
            throw exception
        }
    }

    fun getFile(reportId: Long): InspectionReportFile? = fileRepository.findByReportId(reportId)

    fun resolveFile(file: InspectionReportFile): Path? = DurableFileStorage.resolve(file.storagePath)

    fun replace(report: InspectionReport, originalName: String, contentType: String?, input: java.io.InputStream): InspectionReportFile {
        val safeName = originalName.replace(Regex("[\\r\\n\\u0000]"), "").trim()
        require(safeName.isNotBlank() && safeName.length <= 255) { "File name is invalid." }
        val extension = safeName.substringAfterLast('.', "").lowercase()
        require(extension in setOf("xls", "xlsx")) { "Only XLS or XLSX inspection reports are supported." }
        val previous = getFile(report.id)
        val directory = uploadDirectory("inspection-reports")
        Files.createDirectories(directory)
        val target = directory.resolve("${report.uuid}-${UUID.randomUUID()}.$extension")
        try {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            val size = Files.size(target)
            require(size in 1..20_000_000) { "File size must not exceed 20 MB." }
            DurableFileStorage.persist(target)
            val replacement = InspectionReportFile(
                report.id,
                safeName,
                target.toString(),
                contentType ?: if (extension == "xls") "application/vnd.ms-excel" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                size
            )
            fileRepository.replace(replacement)
            if (previous != null && previous.storagePath != replacement.storagePath) {
                DurableFileStorage.delete(previous.storagePath)
            }
            return replacement
        } catch (exception: Exception) {
            runCatching { DurableFileStorage.delete(target.toString()) }
            throw exception
        }
    }

    /**
     * Reads the structured HSE checklist directly from the original SIR workbook.
     * It is deliberately not copied to the incident register: checklist observations
     * are source facts, not user-created incidents, and must stay in sync with SIR.
     */
    fun healthSafetyObservations(reportId: Long): List<HealthSafetyObservation> {
        val file = getFile(reportId) ?: return emptyList()
        val path = resolveFile(file) ?: return emptyList()
        return try {
            Files.newInputStream(path).use { input ->
                WorkbookFactory.create(input).use(::extractHealthSafetyObservations)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractHealthSafetyObservations(workbook: Workbook): List<HealthSafetyObservation> {
        val formatter = DataFormatter()
        workbook.forEach { sheet ->
            val sectionRow = sheet.firstOrNull { row ->
                row.any { cell -> formatter.formatCellValue(cell).contains("OBSERVANCES ON HEALTH & SAFETY", ignoreCase = true) }
            } ?: return@forEach

            val observations = buildList {
                for (index in (sectionRow.rowNum + 2)..sheet.lastRowNum) {
                    val row = sheet.getRow(index) ?: continue
                    val values = row.map { formatter.formatCellValue(it).trim() }
                    val observation = values.firstOrNull { it.isNotBlank() } ?: continue
                    if (isNextSectionHeading(observation)) break
                    add(
                        HealthSafetyObservation(
                            observation = observation,
                            answer = values.getOrNull(1)?.takeIf { it.isNotBlank() },
                            comment = values.drop(2).filter { it.isNotBlank() }.joinToString(" ").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            return observations
        }
        return emptyList()
    }

    private fun isNextSectionHeading(value: String): Boolean =
        value.startsWith("NARRATIVE ASSESSMENT", ignoreCase = true) ||
            value.startsWith("PHOTO ATTACHMENT", ignoreCase = true) ||
            (value.length > 12 && value == value.uppercase() && value.any(Char::isLetter))

    private fun extractDate(name: String): LocalDate {
        val date = Regex("_(\\d{2}(?:[.-]?\\d{2}){1}[.-]?\\d{4})\\.xlsx?$", RegexOption.IGNORE_CASE)
            .find(name)
            ?.groupValues
            ?.get(1)
            ?: throw IllegalArgumentException("File name must end with _DDMMYYYY.xls/.xlsx or _DD.MM.YYYY.xls/.xlsx.")
        return try {
            LocalDate.parse(date.replace(".", "").replace("-", ""), DateTimeFormatter.ofPattern("ddMMyyyy"))
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid date in SIR file name.")
        }
    }
}
