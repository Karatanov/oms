package oms.ufsi.service

import oms.ufsi.domain.InspectionReport
import oms.ufsi.domain.InspectionReportFile
import oms.ufsi.domain.InspectionPhoto
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
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import oms.ufsi.dto.CreateManualInspectionReportRequest
import oms.ufsi.dto.ManualActivity
import oms.ufsi.dto.ManualHseObservation
import oms.ufsi.dto.ManualPurchasedMaterial
import oms.ufsi.dto.ManualRemark
import oms.ufsi.dto.InspectionReportPreviewResponse
import oms.ufsi.dto.InspectionReportPreviewRow
import oms.ufsi.dto.InspectionReportPreviewSheet
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
    /**
     * Keeps the downloadable workbook for a manually created SIR in sync with
     * its uploaded evidence photographs. The template's sample photo sheet is
     * intentionally removed when a manual report is first created; this sheet
     * is therefore generated solely from photographs belonging to this report.
     */
    @Synchronized
    fun synchronizeManualPhotoSheet(
        report: InspectionReport,
        photos: List<InspectionPhoto>,
        resolveThumbnail: (InspectionPhoto) -> Path?
    ) {
        if (!report.summary.orEmpty().startsWith("Manual SIR")) return
        val source = getFile(report.id) ?: return
        if (!source.originalName.endsWith(".xlsx", ignoreCase = true)) return
        val sourcePath = resolveFile(source) ?: return
        val temporary = Files.createTempFile(sourcePath.parent, "sir-photos-", ".xlsx")
        try {
            WorkbookFactory.create(sourcePath.toFile()).use { workbook ->
                val xlsx = workbook as? XSSFWorkbook ?: return
                workbook.getSheetIndex("Photos")
                    .takeIf { it >= 0 }
                    ?.let(workbook::removeSheetAt)
                val sheet = workbook.createSheet("Photos")
                sheet.setColumnWidth(0, 5_000)
                sheet.setColumnWidth(6, 5_000)
                sheet.createRow(0).apply {
                    createCell(0).setCellValue("Inspection photographs")
                }
                val drawing = sheet.createDrawingPatriarch()
                val helper = workbook.creationHelper
                photos.forEachIndexed { index, photo ->
                    val thumbnail = resolveThumbnail(photo) ?: return@forEachIndexed
                    if (!Files.isRegularFile(thumbnail)) return@forEachIndexed
                    val rowStart = 3 + (index / 2) * 18
                    val columnStart = if (index % 2 == 0) 0 else 6
                    sheet.getRow(rowStart - 1) ?: sheet.createRow(rowStart - 1)
                    sheet.getRow(rowStart - 1).createCell(columnStart).setCellValue(photo.originalName)
                    for (rowIndex in rowStart until rowStart + 15) {
                        (sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)).heightInPoints = 20f
                    }
                    val imageType = if (photo.contentType.contains("png", ignoreCase = true)) {
                        Workbook.PICTURE_TYPE_PNG
                    } else {
                        Workbook.PICTURE_TYPE_JPEG
                    }
                    val pictureIndex = workbook.addPicture(Files.readAllBytes(thumbnail), imageType)
                    val anchor = helper.createClientAnchor().apply {
                        setCol1(columnStart)
                        row1 = rowStart
                        setCol2(columnStart + 5)
                        row2 = rowStart + 15
                    }
                    drawing.createPicture(anchor, pictureIndex)
                }
                Files.newOutputStream(temporary).use(xlsx::write)
            }
            Files.move(temporary, sourcePath, StandardCopyOption.REPLACE_EXISTING)
            DurableFileStorage.persist(sourcePath)
            fileRepository.replace(source.copy(fileSizeBytes = Files.size(sourcePath)))
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

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

    /** Reads the fields of a generated manual SIR back from its workbook. */
    fun readManual(report: InspectionReport): CreateManualInspectionReportRequest {
        val source = getFile(report.id) ?: throw IllegalArgumentException("Original SIR file not found.")
        val path = resolveFile(source) ?: throw IllegalArgumentException("Original SIR file is unavailable.")
        WorkbookFactory.create(path.toFile()).use { workbook ->
            return readSirWorkbook(workbook, report)
        }
    }

    /** Parses fixed SIR template cells for both manual and imported reports. */
    private fun readSirWorkbook(workbook: Workbook, report: InspectionReport): CreateManualInspectionReportRequest {
        val sheet = workbook.getSheet("SIR") ?: workbook.takeIf { it.numberOfSheets > 0 }?.getSheetAt(0)
            ?: throw IllegalArgumentException("SIR sheet is unavailable.")
        val formatter = DataFormatter()
        fun text(row: Int, column: Int) = sheet.getRow(row - 1)?.getCell(column - 1)
            ?.let(formatter::formatCellValue)?.trim().orEmpty()
        fun date(row: Int, column: Int): String = runCatching {
            sheet.getRow(row - 1).getCell(column - 1).localDateTimeCellValue.toLocalDate().toString()
        }.getOrDefault(report.inspectionDate.toString())
        fun rows(start: Int, end: Int, column: Int) = (start..end).map { text(it, column) }.filter(String::isNotBlank)
        val hasPurchasedMaterials = text(28, 1).equals("PURCHASED MATERIALS", ignoreCase = true)
        val contentOffset = if (hasPurchasedMaterials) 5 else 0
        return CreateManualInspectionReportRequest(
            inspectionDate = date(5, 9), inspectionType = report.inspectionType,
            contractor = text(5, 1), contractorRepresentative = text(7, 1).ifBlank { null },
            projectName = text(1, 1).ifBlank { null }, siteReference = text(5, 5).ifBlank { null },
            qaStaff = text(7, 5).ifBlank { null }, usifRepresentative = text(7, 9).ifBlank { null },
            skilledLabor = text(10, 1).ifBlank { null }, unskilledLabor = text(10, 3).ifBlank { null },
            siteManagement = text(10, 5).ifBlank { null }, weather = text(10, 9).ifBlank { null },
            activities = (13..26).mapNotNull { row ->
                val activity = ManualActivity(text(row, 1), text(row, 3), text(row, 8), text(row, 10).ifBlank { null })
                activity.takeIf { it.location.isNotBlank() || it.description.isNotBlank() || !it.remarks.isNullOrBlank() }
            },
            purchasedMaterials = if (hasPurchasedMaterials) (30..32).mapNotNull { row ->
                ManualPurchasedMaterial(text(row, 1), text(row, 4).ifBlank { null }, text(row, 7).ifBlank { null }, text(row, 10).ifBlank { null })
                    .takeIf { it.materialsAndEquipment.isNotBlank() || !it.characteristics.isNullOrBlank() || !it.perDed.isNullOrBlank() || !it.notes.isNullOrBlank() }
            } else emptyList(),
            ongoingObservations = rows(28 + contentOffset, 39 + contentOffset, 1),
            hseObservations = ((41 + contentOffset)..(46 + contentOffset)).mapNotNull { row ->
                text(row, 1).takeIf(String::isNotBlank)?.split(" — ", limit = 3)?.let { parts ->
                    ManualHseObservation(parts[0], parts.getOrNull(1), parts.getOrNull(2))
                }
            },
            qualityRemarks = ((49 + contentOffset)..(53 + contentOffset)).mapNotNull { row ->
                val work = text(row, 1)
                val comments = text(row, 4)
                val rectification = text(row, 7)
                val status = text(row, 10)
                val isLegacyTwoColumnLayout = text(48 + contentOffset, 4).isBlank() && text(48 + contentOffset, 7).isBlank()
                ManualRemark(
                    work = if (isLegacyTwoColumnLayout) "" else work,
                    comment = if (isLegacyTwoColumnLayout) work else comments,
                    rectification = if (isLegacyTwoColumnLayout) status.ifBlank { null } else rectification.ifBlank { null },
                    status = if (isLegacyTwoColumnLayout) null else status.ifBlank { null }
                ).takeIf { it.work.isNotBlank() || it.comment.isNotBlank() || !it.rectification.isNullOrBlank() || !it.status.isNullOrBlank() }
            },
            inspectorName = text(59 + contentOffset, 1), inspectorTitle = text(59 + contentOffset, 4).ifBlank { null },
            latitude = report.latitude, longitude = report.longitude
        )
    }

    /** Rewrites a manual SIR in its existing workbook, preserving its Photos sheet. */
    fun updateManual(report: InspectionReport, project: Project, request: CreateManualInspectionReportRequest): InspectionReport {
        val source = getFile(report.id) ?: throw IllegalArgumentException("Original SIR file not found.")
        val path = resolveFile(source) ?: throw IllegalArgumentException("Original SIR file is unavailable.")
        val temporary = Files.createTempFile(path.parent, "sir-edit-", ".xlsx")
        try {
            WorkbookFactory.create(path.toFile()).use { workbook ->
                val xlsx = workbook as? XSSFWorkbook ?: throw IllegalArgumentException("Manual SIR file must be XLSX.")
                populateManualSir(xlsx, project, request, request.qaStaff?.trim()?.takeIf(String::isNotBlank) ?: request.inspectorName.trim(), removeReferencePhotoSheets = false)
                Files.newOutputStream(temporary).use(xlsx::write)
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            DurableFileStorage.persist(path)
            fileRepository.replace(source.copy(fileSizeBytes = Files.size(path)))
            return reportService.updateReport(
                report.uuid.toString(), request.inspectionDate,
                "Manual SIR [${request.inspectionType}]: ${request.contractor}", report.reportCode,
                request.inspectionType, request.latitude, request.longitude
            ) ?: throw IllegalArgumentException("Inspection report not found.")
        } finally {
            Files.deleteIfExists(temporary)
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
        qaStaff: String,
        removeReferencePhotoSheets: Boolean = true
    ) {
        // The supplied template's second sheet contains photographs from its
        // reference inspection. Generated reports must never carry those
        // unrelated photographs into a new report.
        if (removeReferencePhotoSheets) while (workbook.numberOfSheets > 1) workbook.removeSheetAt(1)
        val sheet = checkNotNull(workbook.getSheet("SIR")) { "Manual SIR sheet is unavailable." }
        val date = LocalDate.parse(request.inspectionDate)

        fun text(row: Int, column: Int, value: String?) {
            val targetRow = sheet.getRow(row - 1) ?: sheet.createRow(row - 1)
            (targetRow.getCell(column - 1) ?: targetRow.createCell(column - 1)).setCellValue(value.orEmpty())
        }
        fun date(row: Int, column: Int) {
            val targetRow = sheet.getRow(row - 1) ?: sheet.createRow(row - 1)
            (targetRow.getCell(column - 1) ?: targetRow.createCell(column - 1)).setCellValue(java.sql.Date.valueOf(date))
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
        val contentOffset = ensurePurchasedMaterialsLayout(sheet)

        // Clear only cells that hold inspection-specific data. Section labels,
        // merged ranges, borders, row heights and print geometry remain intact.
        clear(13..26)
        clear(30..32)
        clear((28 + contentOffset)..(39 + contentOffset))
        clear((41 + contentOffset)..(46 + contentOffset))
        clear((49 + contentOffset)..(53 + contentOffset))
        clear((55 + contentOffset)..(55 + contentOffset))
        text(1, 1, request.projectName?.trim().orEmpty())
        text(5, 1, request.contractor)
        text(5, 5, request.siteReference?.trim().takeIf { !it.isNullOrBlank() }
            ?: listOf(project.siteNumber, project.address ?: project.name).filter(String::isNotBlank).joinToString(", "))
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
        request.purchasedMaterials.take(3).forEachIndexed { index, material ->
            val row = 30 + index
            text(row, 1, material.materialsAndEquipment)
            text(row, 4, material.characteristics)
            text(row, 7, material.perDed)
            text(row, 10, material.notes)
        }
        mergedText(request.ongoingObservations.map(String::trim).filter(String::isNotBlank), 12)
            .forEachIndexed { index, observation -> text(28 + contentOffset + index, 1, observation) }
        mergedText(request.hseObservations.map { observation ->
            listOf(observation.observation, observation.answer, observation.comment)
                .filterNotNull().map(String::trim).filter(String::isNotBlank).joinToString(" — ")
        }.filter(String::isNotBlank), 6).forEachIndexed { index, observation -> text(41 + contentOffset + index, 1, observation) }
        val qualityRemarks = if (request.qualityRemarks.size <= 5) request.qualityRemarks else {
            request.qualityRemarks.take(4) + oms.ufsi.dto.ManualRemark(
                work = request.qualityRemarks.drop(4).joinToString("\n\n") { it.work },
                comment = request.qualityRemarks.drop(4).joinToString("\n\n") { it.comment },
                rectification = request.qualityRemarks.drop(4).mapNotNull { it.rectification?.trim()?.takeIf(String::isNotBlank) }
                    .joinToString("\n\n").ifBlank { null },
                status = request.qualityRemarks.drop(4).mapNotNull { it.status?.trim()?.takeIf(String::isNotBlank) }
                    .joinToString("\n\n").ifBlank { null }
            )
        }
        configureQualityAssessmentColumns(sheet, 48 + contentOffset)
        qualityRemarks.forEachIndexed { index, remark ->
            text(49 + contentOffset + index, 1, remark.work)
            text(49 + contentOffset + index, 4, remark.comment)
            text(49 + contentOffset + index, 7, remark.rectification)
            text(49 + contentOffset + index, 10, remark.status)
        }
        text(59 + contentOffset, 1, request.inspectorName)
        text(59 + contentOffset, 4, request.inspectorTitle)
        date(59 + contentOffset, 6)
    }

    /** Inserts the missing materials table once, without altering reports that already contain it. */
    private fun ensurePurchasedMaterialsLayout(sheet: org.apache.poi.ss.usermodel.Sheet): Int {
        val titleRow = 28
        fun hideLegacyProgressRows() {
            // The four-column quality table replaces the legacy progress and
            // schedule row, which must not appear in newly generated SIRs.
            listOf(59, 60).forEach { row -> sheet.getRow(row - 1)?.zeroHeight = true }
        }
        if (sheet.getRow(titleRow - 1)?.getCell(0)?.let { DataFormatter().formatCellValue(it) }
                ?.equals("PURCHASED MATERIALS", ignoreCase = true) == true) {
            hideLegacyProgressRows()
            return 5
        }

        sheet.shiftRows(titleRow - 1, sheet.lastRowNum, 5, true, false)
        val titleStyle = sheet.getRow(32)?.getCell(0)?.cellStyle
        val headerStyle = sheet.getRow(11)?.getCell(0)?.cellStyle
        val sourceColumns = listOf(0, 2, 7, 9)
        val dataStyles = sourceColumns.map { column -> sheet.getRow(12)?.getCell(column)?.cellStyle }
        fun cell(row: Int, column: Int) = (sheet.getRow(row - 1) ?: sheet.createRow(row - 1)).getCell(column - 1)
            ?: (sheet.getRow(row - 1) ?: sheet.createRow(row - 1)).createCell(column - 1)
        fun mergeRow(row: Int) {
            listOf(0..2, 3..5, 6..8, 9..11).forEach { columns ->
                sheet.addMergedRegion(CellRangeAddress(row - 1, row - 1, columns.first, columns.last))
            }
        }

        sheet.addMergedRegion(CellRangeAddress(titleRow - 1, titleRow - 1, 0, 11))
        cell(titleRow, 1).apply {
            if (titleStyle != null) cellStyle = titleStyle
            setCellValue("PURCHASED MATERIALS")
        }
        mergeRow(29)
        listOf("MATERIALS AND EQUIPMENT", "CHARACTERISTICS", "PER DED? (yes/no)", "NOTES").forEachIndexed { index, label ->
            cell(29, index * 3 + 1).apply {
                if (headerStyle != null) cellStyle = headerStyle
                setCellValue(label)
            }
        }
        (30..32).forEach { row ->
            mergeRow(row)
            dataStyles.forEachIndexed { index, style ->
                cell(row, index * 3 + 1).apply { if (style != null) cellStyle = style }
            }
        }
        hideLegacyProgressRows()
        return 5
    }

    /** Uses the four SIR quality columns while preserving the template's row geometry and styles. */
    private fun configureQualityAssessmentColumns(sheet: org.apache.poi.ss.usermodel.Sheet, headerRow: Int) {
        val qualityRows = headerRow + 1..headerRow + 5
        for (index in sheet.mergedRegions.size - 1 downTo 0) {
            val range = sheet.mergedRegions[index]
            if (range.firstRow + 1 in headerRow..qualityRows.last) sheet.removeMergedRegion(index)
        }
        (headerRow..qualityRows.last).forEach { row ->
            val zeroBasedRow = row - 1
            listOf(0..2, 3..5, 6..8, 9..11).forEach { columns ->
                sheet.addMergedRegion(CellRangeAddress(zeroBasedRow, zeroBasedRow, columns.first, columns.last))
            }
        }
        listOf(
            "WORK",
            "COMMENTS ON QUALITY",
            "RECTIFICATION REMARKS (NNC / NTC)",
            "STATUS OF RECTIFICATION"
        ).forEachIndexed { index, label ->
            sheet.getRow(headerRow - 1).getCell(index * 3).setCellValue(label)
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
     * Converts the meaningful cells of an SIR workbook into a compact payload
     * for the web preview.  Keeping parsing on the server avoids downloading a
     * binary Office file merely to let a user read its contents in the browser.
     */
    fun preview(report: InspectionReport): InspectionReportPreviewResponse {
        val file = getFile(report.id) ?: throw IllegalArgumentException("Original SIR file not found.")
        val path = resolveFile(file) ?: throw IllegalArgumentException("Original SIR file is unavailable.")
        return try {
            Files.newInputStream(path).use { input ->
                WorkbookFactory.create(input).use { workbook ->
                    val formatter = DataFormatter()
                    val evaluator = workbook.creationHelper.createFormulaEvaluator()
                    val manual = runCatching { readSirWorkbook(workbook, report) }.getOrNull()
                        ?.takeIf { parsed ->
                            parsed.projectName?.isNotBlank() == true ||
                                parsed.contractor.isNotBlank() ||
                                parsed.activities.isNotEmpty() ||
                                parsed.hseObservations.isNotEmpty()
                        }
                    val sheets = workbook.map { sheet ->
                        val rows = buildList {
                            for (index in 0..sheet.lastRowNum) {
                                val row = sheet.getRow(index) ?: continue
                                val width = minOf((row.lastCellNum.toInt()).coerceAtLeast(0), 16)
                                val cells = (0 until width).map { column ->
                                    row.getCell(column)?.let { cell ->
                                        formatter.formatCellValue(cell, evaluator).trim()
                                    }.orEmpty()
                                }
                                if (cells.any(String::isNotBlank)) add(InspectionReportPreviewRow(index + 1, cells))
                                if (size >= 180) break
                            }
                        }
                        InspectionReportPreviewSheet(sheet.sheetName, rows)
                    }
                    InspectionReportPreviewResponse(file.originalName, sheets, manual)
                }
            }
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (_: Exception) {
            throw IllegalArgumentException("The SIR file could not be prepared for preview.")
        }
    }

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
