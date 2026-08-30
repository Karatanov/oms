package oms.ufsi.service

import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportFile
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
    fun createManual(projectId: Long, request: CreateManualInspectionReportRequest, createdBy: Long): InspectionReport {
        val report = reportService.createReport(
            projectId, request.inspectionDate, "Manual SIR [${request.inspectionType}]: ${request.contractor}", createdBy,
            inspectionType = request.inspectionType,
            latitude = request.latitude,
            longitude = request.longitude
        )
        val fileName = "SIR-USIF_${request.inspectionDate.replace("-", "")}.xlsx"
        val directory = uploadDirectory("inspection-reports")
        Files.createDirectories(directory)
        val target = directory.resolve("${report.uuid}.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("SIR")
            var rowIndex = 0
            fun row(vararg cells: String?) = sheet.createRow(rowIndex++).apply { cells.forEachIndexed { index, value -> createCell(index).setCellValue(value.orEmpty()) } }
            row("Ukrainian Social Investment Fund (USIF)"); row("SITE INSPECTION REPORT")
            row("CONTRACTOR", request.contractor, "DATE", request.inspectionDate)
            row("CONTRACTOR'S REPRESENTATIVE", request.contractorRepresentative, "M4H QA STAFF", request.qaStaff, "USIF / MOH REPRESENTATIVE", request.usifRepresentative)
            row("SKILLED LABOR", request.skilledLabor, "UNSKILLED LABOR", request.unskilledLabor, "MANAGEMENT ON SITE", request.siteManagement, "WEATHER CONDITIONS", request.weather)
            row("ONGOING ACTIVITIES"); row("BLOCK / LOCATION", "DESCRIPTION OF WORK (PER BOQ ITEM)", "PER SCHEDULE?", "REMARKS")
            request.activities.forEach { row(it.location, it.description, it.onSchedule, it.remarks) }
            row("OBSERVANCES ON ONGOING ACTIVITIES"); request.ongoingObservations.forEach { row(it) }
            row("OBSERVANCES ON HEALTH & SAFETY"); request.hseObservations.forEach { row(it.observation, it.answer, it.comment) }
            row("NARRATIVE ASSESSMENT - COMMENTS ON QUALITY", "RECTIFICATION REMARKS (NNC / NTC)"); request.qualityRemarks.forEach { row(it.comment, it.rectification) }
            row("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS", "SCHEDULE REVISION REMARKS"); row(request.progressComment, request.scheduleRemark)
            row("M4H QA STAFF"); row("NAME", request.inspectorName, "TITLE", request.inspectorTitle, "DATE", request.inspectionDate)
            sheet.setColumnWidth(0, 9000); sheet.setColumnWidth(1, 18000); sheet.setColumnWidth(2, 5000); sheet.setColumnWidth(3, 14000)
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
