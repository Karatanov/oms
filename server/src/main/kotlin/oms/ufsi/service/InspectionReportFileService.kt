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
import java.util.LinkedHashMap
import java.util.UUID
import javax.imageio.ImageIO
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.PrintSetup
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.ss.util.RegionUtil
import org.apache.poi.xssf.usermodel.XSSFPicture
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

/**
 * An evidence image embedded in the `Photo Attachment` worksheet of an
 * imported SIR.  Imported workbooks predate the application's photo registry,
 * so their images have to be materialised as regular inspection photos before
 * the web preview and editor can display them.
 */
data class EmbeddedInspectionPhoto(
    val originalName: String,
    val contentType: String,
    val bytes: ByteArray
)

private const val SIGNATURE_SECTION_TITLE = "UNDP QUALITY ASSURANCE STAFF"

private fun isSignatureSectionTitle(value: String): Boolean =
    value.equals(SIGNATURE_SECTION_TITLE, ignoreCase = true) ||
        value.equals("M4H QA STAFF", ignoreCase = true)

class InspectionReportFileService(
    private val fileRepository: InspectionReportFileRepository,
    private val reportService: InspectionReportService
) {
    /**
     * Opening an XLSX through Apache POI is comparatively expensive on the
     * hosted instance.  A report is immutable while it is being viewed, so
     * retain its parsed representation until the physical workbook changes.
     * The key includes the file version rather than only the report id, which
     * also protects us when a replacement file is uploaded outside this
     * service instance.
     */
    private data class WorkbookCacheKey(
        val reportId: Long,
        val storagePath: String,
        val size: Long,
        val modifiedAt: Long
    )

    private val previewCache = object : LinkedHashMap<WorkbookCacheKey, InspectionReportPreviewResponse>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WorkbookCacheKey, InspectionReportPreviewResponse>?) =
            size > 24
    }
    private val manualCache = object : LinkedHashMap<WorkbookCacheKey, CreateManualInspectionReportRequest>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<WorkbookCacheKey, CreateManualInspectionReportRequest>?) =
            size > 24
    }

    private fun cacheKey(report: InspectionReport, file: InspectionReportFile, path: Path): WorkbookCacheKey =
        WorkbookCacheKey(
            report.id,
            file.storagePath,
            file.fileSizeBytes,
            runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
        )

    private fun invalidateWorkbookCache(reportId: Long) = synchronized(previewCache) {
        previewCache.keys.removeIf { it.reportId == reportId }
        manualCache.keys.removeIf { it.reportId == reportId }
    }

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
        resolveOriginal: (InspectionPhoto) -> Path?
    ) {
        if (!report.summary.orEmpty().startsWith("Manual SIR")) return
        val source = getFile(report.id) ?: return
        if (!source.originalName.endsWith(".xlsx", ignoreCase = true)) return
        val sourcePath = resolveFile(source) ?: return
        val temporary = Files.createTempFile(sourcePath.parent, "sir-photos-", ".xlsx")
        try {
            WorkbookFactory.create(sourcePath.toFile()).use { workbook ->
                val xlsx = workbook as? XSSFWorkbook ?: return
                sequenceOf("Photos", "Photo Attachment").forEach { name ->
                    while (workbook.getSheetIndex(name) >= 0) {
                        workbook.removeSheetAt(workbook.getSheetIndex(name))
                    }
                }
                // Keep evidence legible when printed while avoiding needless
                // paper waste: each portrait A4 page contains two separately
                // captioned images. Captions preserve the relationship between
                // a photo and its corresponding ongoing activity.
                val sheet = workbook.createSheet("Photo Attachment")
                sheet.setColumnWidth(0, 3 * 256)
                sheet.setColumnWidth(14, 2 * 256)
                sheet.printSetup.landscape = false
                sheet.printSetup.paperSize = PrintSetup.A4_PAPERSIZE
                sheet.fitToPage = true
                sheet.autobreaks = false
                sheet.printSetup.fitWidth = 1
                sheet.printSetup.fitHeight = 0
                val drawing = sheet.createDrawingPatriarch()
                val helper = workbook.creationHelper
                // Upload names are generated from the ongoing-work description.
                // Preserve that human-readable link in the sheet, rather than
                // showing an opaque camera filename next to the evidence.
                val baseCaptions = photos.associateWith { inspectionPhotoCaption(it.originalName) }
                val captionTotals = baseCaptions.values.groupingBy { it }.eachCount()
                val captionPositions = mutableMapOf<String, Int>()
                photos.forEachIndexed { index, photo ->
                    // Thumbnails are intended only for the web UI.  A SIR is
                    // formal evidence, so the XLSX must embed the original
                    // uploaded image bytes without downscaling or JPEG
                    // re-encoding.
                    val original = resolveOriginal(photo) ?: return@forEachIndexed
                    if (!Files.isRegularFile(original)) return@forEachIndexed
                    val imageStartRow = 1 + (index % 2) * 17 + (index / 2) * 35
                    val captionRowIndex = imageStartRow + 14
                    val sourceImage = runCatching { ImageIO.read(original.toFile()) }.getOrNull()
                    val portrait = sourceImage?.let { it.height > it.width } ?: false
                    // Shift each image one column to the right from the old
                    // layout and start close to the top print margin.
                    val columnStart = if (portrait) 5 else 3
                    val columnEnd = if (portrait) 11 else 13
                    val caption = requireNotNull(baseCaptions[photo])
                    val captionPosition = (captionPositions[caption] ?: 0) + 1
                    captionPositions[caption] = captionPosition
                    val displayCaption = if ((captionTotals[caption] ?: 0) > 1) {
                        "$caption ($captionPosition/${captionTotals[caption]})"
                    } else caption
                    for (rowIndex in imageStartRow..captionRowIndex) {
                        (sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)).heightInPoints = 18f
                    }
                    val captionRow = sheet.getRow(captionRowIndex) ?: sheet.createRow(captionRowIndex)
                    sheet.addMergedRegion(CellRangeAddress(captionRowIndex, captionRowIndex, 2, 12))
                    captionRow.createCell(2).apply {
                        setCellValue(displayCaption)
                        cellStyle = workbook.createCellStyle().apply {
                            wrapText = true
                            alignment = HorizontalAlignment.CENTER
                            verticalAlignment = VerticalAlignment.CENTER
                        }
                    }
                    val imageType = if (photo.contentType.contains("png", ignoreCase = true)) {
                        Workbook.PICTURE_TYPE_PNG
                    } else {
                        Workbook.PICTURE_TYPE_JPEG
                    }
                    val pictureIndex = workbook.addPicture(Files.readAllBytes(original), imageType)
                    val anchor = helper.createClientAnchor().apply {
                        setCol1(columnStart)
                        row1 = imageStartRow
                        setCol2(columnEnd)
                        row2 = captionRowIndex - 1
                    }
                    drawing.createPicture(anchor, pictureIndex)
                    // A manual break makes the two-photo page arrangement
                    // deterministic in Excel and PDF printing.
                    if (index % 2 == 1 && index + 1 < photos.size) {
                        sheet.setRowBreak(captionRowIndex + 2)
                    }
                }
                Files.newOutputStream(temporary).use(xlsx::write)
            }
            Files.move(temporary, sourcePath, StandardCopyOption.REPLACE_EXISTING)
            DurableFileStorage.persist(sourcePath)
            fileRepository.replace(source.copy(fileSizeBytes = Files.size(sourcePath)))
            invalidateWorkbookCache(report.id)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /**
     * A pending upload is named from its activity description.  The suffix is
     * only an upload disambiguator; it is deliberately omitted from the XLSX
     * caption so every image reads like a comment on the associated work.
     */
    private fun inspectionPhotoCaption(fileName: String): String =
        fileName.substringBeforeLast('.', fileName)
            .replace(Regex("\\s*\\[oms:[^]]+]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[photo\\s+\\d+]$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Photo" }

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
        val key = cacheKey(report, source, path)
        synchronized(previewCache) { manualCache[key] }?.let { return it }
        WorkbookFactory.create(path.toFile()).use { workbook ->
            return readSirWorkbook(workbook, report).also { parsed ->
                synchronized(previewCache) { manualCache[key] = parsed }
            }
        }
    }

    /** Parses fixed SIR template cells for both manual and imported reports. */
    private fun readSirWorkbook(workbook: Workbook, report: InspectionReport): CreateManualInspectionReportRequest {
        val sheet = workbook.getSheet("SIR") ?: workbook.takeIf { it.numberOfSheets > 0 }?.getSheetAt(0)
            ?: throw IllegalArgumentException("SIR sheet is unavailable.")
        val formatter = DataFormatter()
        fun text(row: Int, column: Int) = sheet.getRow(row - 1)?.getCell(column - 1)
            ?.let(formatter::formatCellValue)?.trim().orEmpty()
        // Source SIR workbooks have appeared with both the full-width
        // YES/NO + COMMENTS layout and a compact three-column variant.
        // Read the first populated cell from the relevant group of columns
        // so neither layout loses an answer or a free-text observation.
        fun firstText(row: Int, columns: List<Int>): String? =
            columns.asSequence().map { column -> text(row, column) }.firstOrNull(String::isNotBlank)
        fun date(row: Int, column: Int): String = runCatching {
            sheet.getRow(row - 1).getCell(column - 1).localDateTimeCellValue.toLocalDate().toString()
        }.getOrDefault(report.inspectionDate.toString())
        // Do not scan `lastRowNum` repeatedly. Excel can retain formatting far
        // below a visible SIR, making that value unexpectedly huge. SIR section
        // titles live in column A and within the first 1,000 physical rows; one
        // compact index keeps both preview and edit responsive for imported
        // workbooks.
        val firstColumnRows = sheet.rowIterator().asSequence()
            .takeWhile { it.rowNum < 1_000 }
            .map { row ->
                (row.rowNum + 1) to row.getCell(0)?.let(formatter::formatCellValue).orEmpty().trim()
            }
            .toList()
        fun firstRow(predicate: (String) -> Boolean): Int? =
            firstColumnRows.firstOrNull { (_, value) -> predicate(value) }?.first
        val purchasedMaterialsTitleRow = firstRow {
            it.equals("PURCHASED MATERIALS", ignoreCase = true)
        }
        // Older generated workbooks placed this optional table at row 28,
        // shifting the report's remaining content down. New workbooks place it
        // directly above the signing block, where it belongs.
        val legacyMaterialsAtTop = purchasedMaterialsTitleRow == 28
        val contentOffset = if (legacyMaterialsAtTop) 5 else 0
        // Imported SIRs can contain a different number of current-work rows.
        // Locate their section labels instead of letting observations spill into
        // the activities list because of a fixed row range.
        val activityTitleRow = firstRow { it.equals("ONGOING ACTIVITIES", ignoreCase = true) } ?: 11
        val ongoingObservationsTitleRow = firstRow {
            it.contains("OBSERVANCES ON ONGOING ACTIVITIES", ignoreCase = true)
        } ?: 27 + contentOffset
        val hseTitleRow = firstRow {
            it.contains("OBSERVANCES ON HEALTH & SAFETY", ignoreCase = true)
        } ?: 40 + contentOffset
        val qualityAssessmentTitleRow = firstRow {
            it.contains("NARRATIVE ASSESSMENT", ignoreCase = true) && it.contains("QUALITY", ignoreCase = true)
        } ?: 48 + contentOffset
        val detectedProgressAssessmentTitleRow = firstRow {
            it.contains("NARRATIVE ASSESSMENT", ignoreCase = true) && it.contains("PROGRESS", ignoreCase = true)
        }
        val progressAssessmentTitleRow = detectedProgressAssessmentTitleRow ?: 54 + contentOffset
        val inspectorSectionTitleRow = firstRow(::isSignatureSectionTitle)
        // Quality has five input rows in the approved template. Some imported
        // reports place later section labels much lower in the worksheet, so
        // never scan all the way to a distant Progress title: doing so turns
        // PURCHASED MATERIALS and signature values into quality remarks.
        val qualitySectionEndExclusive = listOfNotNull(
            qualityAssessmentTitleRow + 7,
            detectedProgressAssessmentTitleRow,
            purchasedMaterialsTitleRow,
            inspectorSectionTitleRow
        ).filter { it > qualityAssessmentTitleRow }.minOrNull() ?: qualityAssessmentTitleRow + 7
        val activityEndExclusive = minOf(
            ongoingObservationsTitleRow,
            purchasedMaterialsTitleRow?.takeIf { it < ongoingObservationsTitleRow } ?: Int.MAX_VALUE
        )
        val activityRows = (activityTitleRow + 2 until activityEndExclusive)
        val observationRows = (ongoingObservationsTitleRow + 1 until hseTitleRow)
        return CreateManualInspectionReportRequest(
            inspectionDate = date(5, 9), inspectionType = report.inspectionType,
            contractor = text(5, 1), contractorRepresentative = text(7, 1).ifBlank { null },
            projectName = text(1, 1).ifBlank { null }, siteReference = text(5, 5).ifBlank { null },
            qaStaff = text(7, 5).ifBlank { null }, usifRepresentative = text(7, 9).ifBlank { null },
            skilledLabor = text(10, 1).ifBlank { null }, unskilledLabor = text(10, 3).ifBlank { null },
            siteManagement = text(10, 5).ifBlank { null }, weather = text(10, 9).ifBlank { null },
            activities = activityRows.mapNotNull { row ->
                val activity = ManualActivity(text(row, 1), text(row, 3), text(row, 8), text(row, 10).ifBlank { null })
                activity.takeIf { it.location.isNotBlank() || it.description.isNotBlank() || !it.remarks.isNullOrBlank() }
            },
            purchasedMaterials = purchasedMaterialsTitleRow?.let { titleRow ->
                val endExclusive = inspectorSectionTitleRow?.takeIf { it > titleRow } ?: titleRow + 5
                (titleRow + 2 until endExclusive).mapNotNull { row ->
                    val material = ManualPurchasedMaterial(text(row, 1), text(row, 4).ifBlank { null }, text(row, 7).ifBlank { null }, text(row, 10).ifBlank { null })
                    val isHeader = material.materialsAndEquipment.equals("MATERIALS AND EQUIPMENT", true) ||
                        material.materialsAndEquipment.equals("NAME", true) ||
                        material.materialsAndEquipment.equals("TITLE", true)
                    material.takeUnless { isHeader }?.takeIf {
                        it.materialsAndEquipment.isNotBlank() || !it.characteristics.isNullOrBlank() || !it.perDed.isNullOrBlank() || !it.notes.isNullOrBlank()
                    }
                }
            } ?: emptyList(),
            ongoingObservations = observationRows.map { text(it, 1) }.filter(String::isNotBlank),
            hseObservations = (hseTitleRow + 2 until qualityAssessmentTitleRow).mapNotNull { row ->
                val observation = text(row, 1).takeIf(String::isNotBlank) ?: return@mapNotNull null
                val worksheetAnswer = firstText(row, listOf(8, 7, 2))
                val worksheetComment = firstText(row, listOf(10, 9, 4, 3))
                // Manual workbooks previously stored all three values in the
                // first merged cell. Imported SIRs retain the real YES/NO and
                // COMMENTS columns. Support both without losing custom rows.
                if (worksheetAnswer != null || worksheetComment != null) {
                    ManualHseObservation(observation, worksheetAnswer, worksheetComment)
                } else {
                    observation.split(" — ", limit = 3).let { parts ->
                        ManualHseObservation(parts[0], parts.getOrNull(1), parts.getOrNull(2))
                    }
                }
            },
            qualityRemarks = (qualityAssessmentTitleRow + 1 until qualitySectionEndExclusive).mapNotNull { row ->
                val work = text(row, 1)
                val comments = firstText(row, listOf(4, 2)).orEmpty()
                val rectification = firstText(row, listOf(7, 6)).orEmpty()
                val status = firstText(row, listOf(10, 8)).orEmpty()
                val isHeader = work.equals("WORK", ignoreCase = true) ||
                    work.contains("NARRATIVE ASSESSMENT", ignoreCase = true)
                if (isHeader) return@mapNotNull null
                ManualRemark(
                    work = work,
                    comment = comments,
                    rectification = rectification.ifBlank { null },
                    status = status.ifBlank { null }
                ).takeIf { it.work.isNotBlank() || it.comment.isNotBlank() || !it.rectification.isNullOrBlank() || !it.status.isNullOrBlank() }
            },
            progressComment = text(progressAssessmentTitleRow + 1, 1).ifBlank { null },
            scheduleRemark = text(progressAssessmentTitleRow + 1, 10).ifBlank { null },
            inspectorName = text((inspectorSectionTitleRow ?: 56 + contentOffset) + 3, 1),
            inspectorTitle = text((inspectorSectionTitleRow ?: 56 + contentOffset) + 3, 4).ifBlank { null },
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
            invalidateWorkbookCache(report.id)
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
        // Purchase materials are optional. When present, their table sits
        // directly above the M4H QA signing block.
        val materials = request.purchasedMaterials.filter { material ->
            material.materialsAndEquipment.isNotBlank() || !material.characteristics.isNullOrBlank() ||
                !material.notes.isNullOrBlank()
        }
        // Rebuild this optional block from the form state so removing its last
        // row removes the block from the workbook as well.
        firstRow(sheet, "PURCHASED MATERIALS")?.let { removePurchasedMaterialsLayout(sheet, it) }
        if (materials.isNotEmpty()) ensurePurchasedMaterialsLayout(sheet)
        // The supplied workbook reserves only a handful of H&S rows.  Unlike
        // the other compact sections, this block is explicitly extendable in
        // the manual form, so make room before locating subsequent sections.
        // Previously everything after the reserved capacity was silently
        // dropped while saving the XLSX.
        val hseObservations = request.hseObservations.filter { it.observation.isNotBlank() }
        val initialHseTitleRow = sectionRow(sheet, "OBSERVANCES ON HEALTH & SAFETY")
        val initialQualityTitleRow = sectionRow(sheet, "NARRATIVE ASSESSMENT - COMMENTS ON QUALITY")
        val reservedHseRows = (initialHseTitleRow + 2 until initialQualityTitleRow).count()
        if (hseObservations.size > reservedHseRows) {
            extendHealthSafetySection(
                sheet = sheet,
                qualityTitleRow = initialQualityTitleRow,
                extraRows = hseObservations.size - reservedHseRows
            )
        }
        val activitiesTitleRow = sectionRow(sheet, "ONGOING ACTIVITIES")
        val ongoingObservationsTitleRow = sectionRow(sheet, "OBSERVANCES ON ONGOING ACTIVITIES")
        val hseTitleRow = sectionRow(sheet, "OBSERVANCES ON HEALTH & SAFETY")
        val qualityTitleRow = sectionRow(sheet, "NARRATIVE ASSESSMENT - COMMENTS ON QUALITY")
        val progressTitleRow = sectionRow(sheet, "NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS")
        val signatureTitleRow = signatureSectionRow(sheet)
        val signatureDataRow = signatureTitleRow + 3
        val materialsTitleRow = firstRow(sheet, "PURCHASED MATERIALS")
        fun nextSectionAfter(row: Int, candidates: List<Int?>): Int = candidates
            .filterNotNull()
            .filter { it > row }
            .minOrNull() ?: signatureTitleRow
        val qualityDataEndExclusive = nextSectionAfter(
            qualityTitleRow,
            listOf(progressTitleRow, materialsTitleRow, signatureTitleRow)
        )
        val progressDataEndExclusive = nextSectionAfter(
            progressTitleRow,
            listOf(materialsTitleRow, signatureTitleRow)
        )
        val materialsDataEndExclusive = materialsTitleRow?.let { titleRow ->
            nextSectionAfter(titleRow, listOf(signatureTitleRow))
        }

        // Clear only cells that hold inspection-specific data. Section labels,
        // merged ranges, borders, row heights and print geometry remain intact.
        // Section locations are derived from the template headings.  Fixed
        // row numbers here previously made activity data overwrite the
        // "OBSERVANCES ON ONGOING ACTIVITIES" heading and every following
        // section when an edited report was saved.
        clear((activitiesTitleRow + 2) until ongoingObservationsTitleRow)
        clear((ongoingObservationsTitleRow + 1) until hseTitleRow)
        clear((hseTitleRow + 2) until qualityTitleRow)
        clear((qualityTitleRow + 2) until qualityDataEndExclusive)
        materialsTitleRow?.let { clear((it + 2) until checkNotNull(materialsDataEndExclusive)) }
        clear((progressTitleRow + 1) until progressDataEndExclusive)
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

        val activityRows = (activitiesTitleRow + 2) until ongoingObservationsTitleRow
        val activities = request.activities.take(activityRows.count())
        activities.forEachIndexed { index, activity ->
            val row = activityRows.first + index
            text(row, 1, activity.location)
            text(row, 3, activity.description)
            text(row, 8, activity.onSchedule)
            text(row, 10, activity.remarks)
        }
        val materialsToWrite = materials.take(3)
        materialsToWrite.forEachIndexed { index, material ->
            val row = checkNotNull(materialsTitleRow) + 2 + index
            text(row, 1, material.materialsAndEquipment)
            text(row, 4, material.characteristics)
            // Per DED is binary in the manual form. A missing legacy value
            // means the checkbox was not selected, i.e. "no".
            text(row, 7, material.perDed.orEmpty().trim().ifBlank { "no" })
            text(row, 10, material.notes)
        }
        val ongoingObservationRows = (ongoingObservationsTitleRow + 1) until hseTitleRow
        val ongoingObservations = mergedText(
            request.ongoingObservations.map(String::trim).filter(String::isNotBlank),
            ongoingObservationRows.count()
        )
        ongoingObservations
            .forEachIndexed { index, observation -> text(ongoingObservationRows.first + index, 1, observation) }
        val hseObservationRows = (hseTitleRow + 2) until qualityTitleRow
        check(hseObservations.size <= hseObservationRows.count()) {
            "The SIR health and safety section has insufficient rows."
        }
        hseObservations.forEachIndexed { index, observation ->
            val row = hseObservationRows.first + index
            text(row, 1, observation.observation)
            text(row, 8, observation.answer)
            text(row, 10, observation.comment)
        }
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
        normalizeQualityAssessmentTitle(sheet, qualityTitleRow)
        configureQualityAssessmentColumns(sheet, qualityTitleRow + 1)
        val qualityRows = (qualityTitleRow + 2) until qualityDataEndExclusive
        qualityRemarks.take(qualityRows.count()).forEachIndexed { index, remark ->
            val row = qualityRows.first + index
            text(row, 1, remark.work)
            text(row, 4, remark.comment)
            text(row, 7, remark.rectification)
            text(row, 10, remark.status)
        }
        text(progressTitleRow + 1, 1, request.progressComment)
        text(progressTitleRow + 1, 10, request.scheduleRemark)
        text(signatureDataRow, 1, request.inspectorName)
        text(signatureDataRow, 4, request.inspectorTitle)
        date(signatureDataRow, 6)

        // A section begins immediately after the data in the previous one.
        // Trim unused template rows from bottom to top so shifting a section
        // never invalidates the positions still to be processed.
        if (materialsTitleRow != null) collapseUnusedSectionRows(
            sheet, "PURCHASED MATERIALS", 2,
            listOf("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS", SIGNATURE_SECTION_TITLE),
            materialsToWrite.size
        )
        collapseUnusedSectionRows(
            sheet, "NARRATIVE ASSESSMENT - COMMENTS ON QUALITY", 2,
            listOf("NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS", "PURCHASED MATERIALS", SIGNATURE_SECTION_TITLE),
            qualityRemarks.size
        )
        collapseUnusedSectionRows(
            sheet, "OBSERVANCES ON HEALTH & SAFETY", 2,
            listOf("NARRATIVE ASSESSMENT - COMMENTS ON QUALITY"),
            hseObservations.size
        )
        collapseUnusedSectionRows(
            sheet, "OBSERVANCES ON ONGOING ACTIVITIES", 1,
            listOf("OBSERVANCES ON HEALTH & SAFETY"),
            ongoingObservations.size
        )
        collapseUnusedSectionRows(
            sheet, "ONGOING ACTIVITIES", 2,
            listOf("OBSERVANCES ON ONGOING ACTIVITIES"),
            activities.size
        )
        // Row shifts and merged-cell rewrites performed above can drop part
        // of a merged heading's border in Excel. Locate their final rows and
        // repaint the full medium-weight contour from the approved template.
        firstRow(sheet, "PURCHASED MATERIALS")?.let { reinforcePurchasedMaterialsBorders(sheet, it) }
        reinforceProgressAssessmentBorders(
            sheet,
            sectionRow(sheet, "NARRATIVE ASSESSMENT - COMMENTS ON PROGRESS")
        )
        // The old M4H wording was part of the supplied sample, not the
        // current report template. Preserve the layout, but publish UNDP.
        sheet.getRow(signatureSectionRow(sheet) - 1)?.getCell(0)?.setCellValue(SIGNATURE_SECTION_TITLE)
    }

    private fun applyMediumOutline(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        row: Int,
        firstColumn: Int,
        lastColumn: Int
    ) {
        val region = CellRangeAddress(row - 1, row - 1, firstColumn, lastColumn)
        RegionUtil.setBorderTop(BorderStyle.MEDIUM, region, sheet)
        RegionUtil.setBorderBottom(BorderStyle.MEDIUM, region, sheet)
        RegionUtil.setBorderLeft(BorderStyle.MEDIUM, region, sheet)
        RegionUtil.setBorderRight(BorderStyle.MEDIUM, region, sheet)
    }

    /** Keeps the optional materials title enclosed after inserting its rows. */
    private fun reinforcePurchasedMaterialsBorders(sheet: org.apache.poi.ss.usermodel.Sheet, titleRow: Int) {
        applyMediumOutline(sheet, titleRow, 0, 11)
        // The four headings are separate merged cells; make each grid cell
        // complete as well, rather than relying on a single top-left style.
        listOf(0..2, 3..5, 6..8, 9..11).forEach { columns ->
            applyMediumOutline(sheet, titleRow + 1, columns.first, columns.last)
        }
    }

    /** Both progress-assessment headings must retain a closed, bold outline. */
    private fun reinforceProgressAssessmentBorders(sheet: org.apache.poi.ss.usermodel.Sheet, titleRow: Int) {
        listOf(0..8, 9..11).forEach { columns ->
            applyMediumOutline(sheet, titleRow, columns.first, columns.last)
        }
    }

    /** Inserts the optional materials table immediately before the signature. */
    private fun ensurePurchasedMaterialsLayout(sheet: org.apache.poi.ss.usermodel.Sheet) {
        val existingTitleRow = firstRow(sheet, "PURCHASED MATERIALS")
        if (existingTitleRow != null) return
        val titleRow = signatureSectionRow(sheet)
        val titleStyle = sheet.getRow(titleRow - 1)?.getCell(0)?.cellStyle
        val headerStyle = sheet.getRow(11)?.getCell(0)?.cellStyle
        val sourceColumns = listOf(0, 2, 7, 9)
        val dataStyles = sourceColumns.map { column -> sheet.getRow(12)?.getCell(column)?.cellStyle }
        // The template reserves nine rows for this optional section: a title,
        // a header, three data rows and four spacer rows.  Preserve that
        // geometry, otherwise all following sections shift upward.
        sheet.shiftRows(titleRow - 1, sheet.lastRowNum, 9, true, false)
        fun cell(row: Int, column: Int) = (sheet.getRow(row - 1) ?: sheet.createRow(row - 1)).getCell(column - 1)
            ?: (sheet.getRow(row - 1) ?: sheet.createRow(row - 1)).createCell(column - 1)
        fun mergeRow(row: Int, border: BorderStyle = BorderStyle.THIN) {
            listOf(0..2, 3..5, 6..8, 9..11).forEach { columns ->
                val region = CellRangeAddress(row - 1, row - 1, columns.first, columns.last)
                sheet.addMergedRegion(region)
                // POI only retains the top-left cell style after a merge.
                // Explicitly paint every edge, otherwise the right/bottom
                // lines of newly inserted Purchased Materials rows disappear
                // when the workbook is opened in Excel.
                RegionUtil.setBorderTop(border, region, sheet)
                RegionUtil.setBorderBottom(border, region, sheet)
                RegionUtil.setBorderLeft(border, region, sheet)
                RegionUtil.setBorderRight(border, region, sheet)
            }
        }

        sheet.addMergedRegion(CellRangeAddress(titleRow - 1, titleRow - 1, 0, 11))
        cell(titleRow, 1).apply {
            if (titleStyle != null) cellStyle = titleStyle
            setCellValue("PURCHASED MATERIALS")
        }
        applyMediumOutline(sheet, titleRow, 0, 11)
        // The source SIR uses a heavier grid for the column headings, making
        // the optional materials table distinguishable from its data rows.
        mergeRow(titleRow + 1, BorderStyle.MEDIUM)
        listOf("MATERIALS AND EQUIPMENT", "CHARACTERISTICS", "PER DED? (yes/no)", "NOTES").forEachIndexed { index, label ->
            cell(titleRow + 1, index * 3 + 1).apply {
                if (headerStyle != null) cellStyle = headerStyle
                setCellValue(label)
            }
        }
        (titleRow + 2..titleRow + 8).forEach { row ->
            mergeRow(row)
            dataStyles.forEachIndexed { index, style ->
                cell(row, index * 3 + 1).apply { if (style != null) cellStyle = style }
            }
        }
    }

    /** Restores the surrounding row geometry after removing an optional materials table. */
    private fun removePurchasedMaterialsLayout(sheet: org.apache.poi.ss.usermodel.Sheet, titleRow: Int) {
        removeMergedRows(sheet, titleRow..(titleRow + 8))
        sheet.shiftRows(titleRow + 8, sheet.lastRowNum, -9, true, false)
    }

    private fun firstRow(sheet: org.apache.poi.ss.usermodel.Sheet, value: String): Int? =
        (1..(sheet.lastRowNum + 1)).firstOrNull { row ->
            sheet.getRow(row - 1)?.getCell(0)?.let { DataFormatter().formatCellValue(it).trim() }?.let { cell ->
                if (value == SIGNATURE_SECTION_TITLE) isSignatureSectionTitle(cell)
                else cell.equals(value, ignoreCase = true)
            } == true
        }

    private fun sectionRow(sheet: org.apache.poi.ss.usermodel.Sheet, title: String): Int =
        requireNotNull(firstRow(sheet, title)) { "SIR template is missing the '$title' section." }

    private fun signatureSectionRow(sheet: org.apache.poi.ss.usermodel.Sheet): Int =
        requireNotNull((1..(sheet.lastRowNum + 1)).firstOrNull { row ->
            sheet.getRow(row - 1)?.getCell(0)?.let { DataFormatter().formatCellValue(it).trim() }
                ?.let(::isSignatureSectionTitle) == true
        }) { "SIR template is missing the '$SIGNATURE_SECTION_TITLE' section." }

    /**
     * Inserts formatted H&S table rows directly before the next section.
     *
     * The template has a finite set of preformatted rows, while the UI allows
     * an inspector to record additional observations. Copying the final
     * reserved row's style and merged-cell geometry preserves the document's
     * visual layout without sacrificing observations that exceed that reserve.
     */
    private fun extendHealthSafetySection(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        qualityTitleRow: Int,
        extraRows: Int
    ) {
        if (extraRows <= 0) return
        // Inputs are one-based; Apache POI row indices are zero-based.
        val sourceRowIndex = qualityTitleRow - 2
        val sourceRow = sheet.getRow(sourceRowIndex)
            ?: error("SIR template is missing the final health and safety row.")
        val sourceMerges = sheet.mergedRegions
            .filter { it.firstRow == sourceRowIndex && it.lastRow == sourceRowIndex }
            .map { range ->
                CellRangeAddress(range.firstRow, range.lastRow, range.firstColumn, range.lastColumn)
            }
        val insertionIndex = qualityTitleRow - 1
        sheet.shiftRows(insertionIndex, sheet.lastRowNum, extraRows, true, false)
        repeat(extraRows) { offset ->
            val targetIndex = insertionIndex + offset
            val targetRow = sheet.getRow(targetIndex) ?: sheet.createRow(targetIndex)
            targetRow.height = sourceRow.height
            (0..11).forEach { column ->
                val sourceCell = sourceRow.getCell(column) ?: return@forEach
                val targetCell = targetRow.getCell(column) ?: targetRow.createCell(column)
                targetCell.cellStyle = sourceCell.cellStyle
                targetCell.setBlank()
            }
            sourceMerges.forEach { sourceRange ->
                sheet.addMergedRegion(
                    CellRangeAddress(
                        targetIndex,
                        targetIndex,
                        sourceRange.firstColumn,
                        sourceRange.lastColumn
                    )
                )
            }
        }
    }

    /** Removes blank reserved rows so the next section follows the actual data. */
    private fun collapseUnusedSectionRows(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        sectionTitle: String,
        firstDataRowOffset: Int,
        nextSectionTitles: List<String>,
        usedRows: Int
    ) {
        val titleRow = sectionRow(sheet, sectionTitle)
        val nextTitleRow = nextSectionTitles.mapNotNull { firstRow(sheet, it) }
            .filter { it > titleRow }
            .minOrNull() ?: return
        val firstDataRow = titleRow + firstDataRowOffset
        val availableRows = nextTitleRow - firstDataRow
        val rowsToRemove = (availableRows - usedRows).coerceAtLeast(0)
        if (rowsToRemove == 0) return
        val removedRows = (firstDataRow + usedRows)..(nextTitleRow - 1)
        removeMergedRows(sheet, removedRows)
        // POI uses zero-based indices here. nextTitleRow - 1 is the first
        // row after the deleted 1-based range.
        sheet.shiftRows(nextTitleRow - 1, sheet.lastRowNum, -rowsToRemove, true, false)
    }

    private fun removeMergedRows(sheet: org.apache.poi.ss.usermodel.Sheet, rows: IntRange) {
        for (index in sheet.mergedRegions.size - 1 downTo 0) {
            val range = sheet.mergedRegions[index]
            if (range.firstRow + 1 in rows || range.lastRow + 1 in rows) sheet.removeMergedRegion(index)
        }
    }

    /** Restores the single full-width title cell for the quality assessment section. */
    private fun normalizeQualityAssessmentTitle(sheet: org.apache.poi.ss.usermodel.Sheet, titleRow: Int) {
        for (index in sheet.mergedRegions.size - 1 downTo 0) {
            val range = sheet.mergedRegions[index]
            if (range.firstRow + 1 == titleRow || range.lastRow + 1 == titleRow) sheet.removeMergedRegion(index)
        }
        val row = sheet.getRow(titleRow - 1) ?: sheet.createRow(titleRow - 1)
        (1..11).forEach { column -> (row.getCell(column) ?: row.createCell(column)).setBlank() }
        row.getCell(0).setCellValue("NARRATIVE ASSESSMENT - COMMENTS ON QUALITY")
        sheet.addMergedRegion(CellRangeAddress(titleRow - 1, titleRow - 1, 0, 11))
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
            val currentRow = sheet.getRow(zeroBasedRow) ?: sheet.createRow(zeroBasedRow)
            (0..11).forEach { column -> (currentRow.getCell(column) ?: currentRow.createCell(column)).setBlank() }
            listOf(0..2, 3..5, 6..8, 9..11).forEach { columns ->
                val region = CellRangeAddress(zeroBasedRow, zeroBasedRow, columns.first, columns.last)
                sheet.addMergedRegion(region)
                // A merged range only keeps its top-left style in Apache POI.
                // Paint every edge explicitly so empty quality rows remain a
                // complete table when the generated SIR is opened in Excel.
                RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet)
                RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet)
                RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet)
                RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet)
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
     * Reads evidence pictures from the sheet used by the official SIR template.
     * `Photos` is retained as a compatibility fallback for workbooks generated
     * by an older OMS build.  The text directly below each picture becomes the
     * caption/filename, preserving the relation to an ongoing activity.
     */
    fun extractEmbeddedPhotos(report: InspectionReport): List<EmbeddedInspectionPhoto> {
        val file = getFile(report.id) ?: return emptyList()
        if (!file.originalName.endsWith(".xlsx", ignoreCase = true)) return emptyList()
        val path = resolveFile(file) ?: return emptyList()
        return runCatching {
            Files.newInputStream(path).use { input ->
                WorkbookFactory.create(input).use { workbook ->
                    val xlsx = workbook as? XSSFWorkbook ?: return emptyList()
                    val sheet = xlsx.getSheet("Photo Attachment") ?: xlsx.getSheet("Photos") ?: return emptyList()
                    val formatter = DataFormatter()
                    val occurrences = mutableMapOf<String, Int>()
                    sheet.drawingPatriarch.shapes
                        .filterIsInstance<XSSFPicture>()
                        .sortedBy { it.clientAnchor.row1 }
                        .mapNotNull { picture ->
                            val extension = picture.pictureData.suggestFileExtension().lowercase()
                                .let { if (it == "jpeg") "jpg" else it }
                            if (extension !in setOf("jpg", "png")) return@mapNotNull null
                            val caption = embeddedPhotoCaption(sheet, picture.clientAnchor.row1, formatter)
                                .ifBlank { "Photo" }
                            val sequence = (occurrences[caption] ?: 0) + 1
                            occurrences[caption] = sequence
                            val safeCaption = caption
                                .replace(Regex("[\\r\\n\\u0000]"), " ")
                                .trim()
                                .take(220)
                                .ifBlank { "Photo" }
                            EmbeddedInspectionPhoto(
                                originalName = "$safeCaption [photo $sequence].$extension",
                                contentType = if (extension == "png") "image/png" else "image/jpeg",
                                bytes = picture.pictureData.data
                            )
                        }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun embeddedPhotoCaption(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        imageStartRow: Int,
        formatter: DataFormatter
    ): String {
        // The official template uses one-cell anchors: Excel does not expose a
        // reliable bottom row for the image.  Its caption is consistently in
        // the next 35 rows, so find the first non-empty row after the anchor.
        for (rowIndex in imageStartRow..minOf(sheet.lastRowNum, imageStartRow + 35)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val values = (0 until row.lastCellNum.coerceAtLeast(0).toInt())
                .mapNotNull { column ->
                    formatter.formatCellValue(row.getCell(column)).trim().takeIf(String::isNotBlank)
                }
            if (values.isNotEmpty()) return values.joinToString(" ")
        }
        return ""
    }

    /**
     * Converts the meaningful cells of an SIR workbook into a compact payload
     * for the web preview.  Keeping parsing on the server avoids downloading a
     * binary Office file merely to let a user read its contents in the browser.
     */
    fun preview(report: InspectionReport): InspectionReportPreviewResponse {
        val file = getFile(report.id) ?: throw IllegalArgumentException("Original SIR file not found.")
        val path = resolveFile(file) ?: throw IllegalArgumentException("Original SIR file is unavailable.")
        val key = cacheKey(report, file, path)
        synchronized(previewCache) { previewCache[key] }?.let { return it }
        // Manual reports are rendered by ReadOnlySirReport, not by the generic
        // workbook grid.  Parsing every worksheet and evaluating formulas after
        // reading the manual fields doubled the initial wait time for no UI
        // benefit.  Return the compact form payload directly instead.
        if (report.summary.orEmpty().startsWith("Manual SIR")) {
            val response = InspectionReportPreviewResponse(file.originalName, emptyList(), readManual(report))
            synchronized(previewCache) { previewCache[key] = response }
            return response
        }
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
                    InspectionReportPreviewResponse(file.originalName, sheets, manual).also { response ->
                        synchronized(previewCache) { previewCache[key] = response }
                    }
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
            invalidateWorkbookCache(report.id)
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
        fun cellText(row: org.apache.poi.ss.usermodel.Row, column: Int): String =
            row.getCell(column - 1)?.let(formatter::formatCellValue)?.trim().orEmpty()
        fun isChecklistAnswer(value: String): Boolean = value.trim().lowercase() in setOf(
            "yes", "no", "y", "n", "так", "ні", "true", "false", "1", "0"
        )
        workbook.forEach { sheet ->
            val sectionRow = sheet.firstOrNull { row ->
                row.any { cell -> formatter.formatCellValue(cell).contains("OBSERVANCES ON HEALTH & SAFETY", ignoreCase = true) }
            } ?: return@forEach

            val observations = buildList {
                for (index in (sectionRow.rowNum + 2)..sheet.lastRowNum) {
                    val row = sheet.getRow(index) ?: continue
                    val observation = cellText(row, 1)
                    if (observation.isBlank()) continue
                    if (isNextSectionHeading(observation)) break
                    // The official template keeps the checklist values in
                    // H (YES/NO) and J (COMMENTS); compact legacy SIRs use
                    // B and C instead. Iterating Row cells is unsafe here:
                    // styled blank cells make their collection indices differ
                    // from their spreadsheet columns and can turn "yes" into
                    // a red free-text comment.
                    val candidate = listOf(8, 7, 2).asSequence()
                        .map { column -> cellText(row, column) }
                        .firstOrNull(String::isNotBlank).orEmpty()
                    val answer = candidate.takeIf(::isChecklistAnswer)
                    val comment = buildList {
                        if (candidate.isNotBlank() && answer == null) add(candidate)
                        listOf(10, 9, 4, 3).asSequence()
                            .map { column -> cellText(row, column) }
                            .firstOrNull(String::isNotBlank)
                            ?.takeIf { it != candidate }
                            ?.let(::add)
                    }.joinToString(" ").takeIf(String::isNotBlank)
                    add(
                        HealthSafetyObservation(
                            observation = observation,
                            answer = answer,
                            comment = comment
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
