package oms.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oms.data.ApiInspectionReport
import oms.data.ApiInspectionReportPreview
import oms.data.ManualInspectionReportRequest
import oms.data.CreateInspectionFindingRequest
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.data.UpdateInspectionFindingRequest
import oms.data.ApiInspectionAnalytics
import oms.components.SortableTableHeader
import oms.components.ReportStatusChip
import oms.components.TableActionIconButton
import oms.components.FilterDropdown
import oms.components.InlineOptionPicker
import oms.components.SearchableOptionPicker
import oms.components.ExpandableTableText
import oms.components.OmsDateField
import oms.components.toOmsDate
import oms.components.WasmSafeOverlay
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.model.localizedName
import kotlin.js.JsName

@JsName("openInspectionPhotoUpload")
external fun openInspectionPhotoUpload(reportUuid: String, onComplete: (String) -> Unit)

@JsName("openInspectionSourceReplacement")
external fun openInspectionSourceReplacement(reportUuid: String, onComplete: (String) -> Unit)

private data class ReportRow(
    val projectUuid: String,
    val projectName: String,
    val subprojectName: String?,
    val subprojectNameEn: String?,
    val subprojectCode: String?,
    val subprojectPartCode: String?,
    val report: ApiInspectionReport
)

private fun ReportRow.localizedSubprojectName(): String? =
    if (LocalizationManager.currentLanguage == Language.EN) subprojectNameEn?.takeIf(String::isNotBlank) ?: subprojectName
    else subprojectName
private fun ReportRow.title(): String = "${localizedSubprojectName() ?: projectName} ${report.inspectionDate.toOmsDate()}"
private enum class ReportSort { Date, Subproject, SubprojectCode, SubprojectPartCode, Status, Author }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReportsScreen(
    onNewInspection: () -> Unit = {},
    onEditInspection: (String) -> Unit = {},
    onPreviewInspection: (String) -> Unit = {},
    canCreateReports: Boolean = true,
    canReviewReports: Boolean = true,
    canMoveReports: Boolean = true
) {
    val deletion = oms.components.LocalDeleteConfirmation.current
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var subprojectFilter by remember { mutableStateOf<String?>(null) }
    var subprojectCodeFilter by remember { mutableStateOf<String?>(null) }
    var subprojectPartCodeFilter by remember { mutableStateOf<String?>(null) }
    var authorFilter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportToEdit by remember { mutableStateOf<ReportRow?>(null) }
    var findingsReport by remember { mutableStateOf<ReportRow?>(null) }
    var reportToReview by remember { mutableStateOf<ReportRow?>(null) }
    var analytics by remember { mutableStateOf<ApiInspectionAnalytics?>(null) }
    var inspectionsChartExpanded by remember { mutableStateOf(true) }
    var eshsChartExpanded by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(ReportSort.Date) }
    var ascending by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val contentScrollState = rememberScrollState()
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        loading = true; loadFailed = false
        try {
        val reportItems = coroutineScope {
            val loadReports = async { OmsApiClient.inspectionReports() }
            val loadAnalytics = async { runCatching { OmsApiClient.inspectionAnalytics() }.getOrNull() }
            analytics = loadAnalytics.await()
            loadReports.await()
        }
        // No project context is required for an empty registry.  This keeps the
        // first Reports visit lightweight on a fresh deployment.
        if (reportItems.isNotEmpty()) ProjectRepository.refresh()
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        reports = reportItems.mapNotNull { item ->
            val attachedProject = projectsById[item.projectUuid] ?: return@mapNotNull null
            val ancestry = generateSequence(attachedProject) { current ->
                current.parentProjectUuid?.let(projectsById::get)
            }.toList().asReversed()
            val root = ancestry.firstOrNull() ?: attachedProject
            val projectName = root.name
            val subprojectName = ancestry.getOrNull(1)?.name
            val subprojectNameEn = ancestry.getOrNull(1)?.nameEn
            val subprojectCode = ancestry.getOrNull(1)?.siteNumber
            val subprojectPartCode = ancestry.getOrNull(2)?.siteNumber
            ReportRow(attachedProject.id, projectName, subprojectName, subprojectNameEn, subprojectCode, subprojectPartCode, item.report)
        }.sortedByDescending { it.report.inspectionDate }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            loadFailed = true
        } finally { loading = false }
    }
    val language = LocalizationManager.currentLanguage
    val visible = remember(reports, search, status, subprojectFilter, subprojectCodeFilter, subprojectPartCodeFilter, authorFilter, sort, ascending, language) {
        reports.asSequence().filter {
                (search.isBlank() || it.report.inspectionCode.contains(search, true) || it.title().contains(search, true) || it.subprojectCode.orEmpty().contains(search, true)) &&
                (status == null || it.report.status == status) &&
                (subprojectFilter == null || it.localizedSubprojectName() == subprojectFilter) &&
                (subprojectCodeFilter == null || it.subprojectCode == subprojectCodeFilter) &&
                (subprojectPartCodeFilter == null || it.subprojectPartCode == subprojectPartCodeFilter) &&
                (authorFilter == null || it.report.authorUsername == authorFilter)
        }.sortedWith(
            compareBy<ReportRow> {
                when (sort) {
                    ReportSort.Date -> it.report.inspectionDate
                    ReportSort.Subproject -> it.localizedSubprojectName().orEmpty()
                    ReportSort.SubprojectCode -> it.subprojectCode.orEmpty()
                    ReportSort.SubprojectPartCode -> it.subprojectPartCode.orEmpty()
                    ReportSort.Status -> it.report.status
                    ReportSort.Author -> it.report.authorUsername.orEmpty()
                }
            }.let { if (ascending) it else it.reversed() }
        ).toList()
    }
    fun selectSort(column: ReportSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    fun scrollBy(delta: Float) = scope.launch { contentScrollState.animateScrollBy(delta) }

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { scrollBy(-420f); true }
                    Key.DirectionDown -> { scrollBy(420f); true }
                    Key.PageUp -> { scrollBy(-720f); true }
                    Key.PageDown -> { scrollBy(720f); true }
                    Key.MoveHome -> { scope.launch { contentScrollState.animateScrollTo(0) }; true }
                    Key.MoveEnd -> { scope.launch { contentScrollState.animateScrollTo(contentScrollState.maxValue) }; true }
                    else -> false
                }
            }
            .verticalScroll(contentScrollState)
            .padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        oms.components.PageHeading(LocalizationManager.t("reports_title"), Icons.Default.FactCheck) {
            if (canCreateReports) Button(onClick = onNewInspection) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(LocalizationManager.t("new_inspection")) }
        }
        OutlinedTextField(search, { search = it }, singleLine = true, label = { Text(LocalizationManager.t("reports_search")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        if (loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        if (loadFailed) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { reloadKey++ })
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        oms.components.AdaptiveChartRow(
            first = {
                MetricsChart(
                    "inspections_by_month", "inspections_by_month_hint", analytics?.monthlyInspectionCounts.orEmpty(),
                    compact = true, expanded = inspectionsChartExpanded, onExpandedChange = { inspectionsChartExpanded = it }
                )
            },
            second = {
                MetricsChart(
                    "eshs_violations_by_month", "eshs_violations_by_month_hint", analytics?.monthlyEshsViolations.orEmpty(),
                    compact = true, expanded = eshsChartExpanded, onExpandedChange = { eshsChartExpanded = it }
                )
            }
        )
        // Card clips translated children in Web/Wasm.  A drawn surface keeps
        // the same light table background while allowing its header to stick
        // above the scrolling page.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            oms.components.ScrollableTable(
                Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                pageScrollState = contentScrollState,
                header = {
                    ReportTableHeader(
                        sort = sort,
                        ascending = ascending,
                        onSort = ::selectSort,
                        reports = reports,
                        subprojectFilter = subprojectFilter,
                        onSubprojectFilterChange = { subprojectFilter = it; subprojectPartCodeFilter = null },
                        subprojectCodeFilter = subprojectCodeFilter,
                        onSubprojectCodeFilterChange = { subprojectCodeFilter = it },
                        subprojectPartCodeFilter = subprojectPartCodeFilter,
                        onSubprojectPartCodeFilterChange = { subprojectPartCodeFilter = it },
                        statusFilter = status,
                        onStatusFilterChange = { status = it },
                        authorFilter = authorFilter,
                        onAuthorFilterChange = { authorFilter = it }
                    )
                    HorizontalDivider()
                }
            ) {
                if (!loading && !loadFailed && visible.isEmpty()) Text(LocalizationManager.t("no_reports"))
                visible.forEach { row ->
                    Row(Modifier.width(1_163.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.report.inspectionDate.toOmsDate(), Modifier.width(105.dp))
                        ExpandableTableText(row.localizedSubprojectName() ?: "—", Modifier.width(220.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.subprojectCode ?: "—", Modifier.width(150.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.subprojectPartCode ?: "—", Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.width(130.dp)) { ReportStatusChip(row.report.status) }
                        Text(row.report.authorUsername ?: "—", Modifier.width(110.dp))
                        if (canCreateReports) TableActionIconButton(LocalizationManager.t("edit_inspection"), Icons.Default.Edit) { onEditInspection(row.report.uuid) }
                        else Spacer(Modifier.width(48.dp))
                        if (canReviewReports && row.report.status == "pending_review") {
                            TableActionIconButton(LocalizationManager.t("review_report"), Icons.Default.RateReview) { reportToReview = row }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                        TableActionIconButton(LocalizationManager.t("preview_report"), Icons.Default.Visibility) { onPreviewInspection(row.report.uuid) }
                        TableActionIconButton(LocalizationManager.t("open_source_file"), Icons.Default.FileDownload) {
                            uriHandler.openUri(oms.data.omsApiUrl("/inspection-reports/${row.report.uuid}/source-file"))
                        }
                        if (canCreateReports) {
                            TableActionIconButton(LocalizationManager.t("findings"), Icons.AutoMirrored.Filled.FactCheck) { findingsReport = row }
                        } else Spacer(Modifier.width(48.dp))
                        if (canCreateReports) TableActionIconButton(LocalizationManager.t("delete_report"), Icons.Default.Delete) {
                            deletion.show(row.title()) { scope.launch {
                                if (OmsApiClient.deleteInspectionReport(row.report.uuid)) reports = reports.filterNot { it.report.uuid == row.report.uuid }
                                else errorMessage = LocalizationManager.t("error_delete_report")
                            } }
                        } else Spacer(Modifier.width(48.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    reportToEdit?.let { report -> WasmSafeOverlay(onDismiss = { reportToEdit = null }, errorMessage = errorMessage) {
        ReportEditorDialog(
            report = report,
            canMoveReports = canMoveReports,
            canChangeStatus = canReviewReports,
            onDismiss = { reportToEdit = null },
            onSave = { date, summary, inspectionType, latitude, longitude, targetProjectUuid, requestedStatus ->
                scope.launch {
                    runCatching {
                        var updated = OmsApiClient.updateInspectionReport(
                            report.report.uuid, date, summary, null, inspectionType, latitude, longitude
                        )
                        if (targetProjectUuid != report.projectUuid && !OmsApiClient.moveInspectionReport(report.report.uuid, targetProjectUuid)) {
                            throw IllegalStateException(LocalizationManager.t("error_move_report"))
                        }
                        if (requestedStatus != null && requestedStatus != updated.status) {
                            updated = OmsApiClient.updateInspectionReportStatus(report.report.uuid, requestedStatus)
                        }
                        updated
                    }
                        .onSuccess { updated ->
                            val target = ProjectRepository.projects.firstOrNull { it.id == targetProjectUuid }
                            reports = reports.map {
                                if (it.report.uuid != updated.uuid) it else {
                                    val ancestry = target?.let { selected ->
                                        generateSequence(selected) { current -> current.parentProjectUuid?.let { parentUuid -> ProjectRepository.projects.firstOrNull { project -> project.id == parentUuid } } }
                                            .toList().asReversed()
                                    }.orEmpty()
                                    it.copy(
                                        projectUuid = targetProjectUuid,
                                        projectName = ancestry.firstOrNull()?.name ?: it.projectName,
                                        subprojectName = ancestry.getOrNull(1)?.name,
                                        subprojectNameEn = ancestry.getOrNull(1)?.nameEn,
                                        subprojectCode = ancestry.getOrNull(1)?.siteNumber,
                                        subprojectPartCode = ancestry.getOrNull(2)?.siteNumber,
                                        report = updated
                                    )
                                }
                            }
                            reportToEdit = null
                        }
                        .onFailure {
                            errorMessage = LocalizationManager.t("error_update_report")
                                .replace("{message}", it.message ?: LocalizationManager.t("unknown_error"))
                        }
                }
            }
        )
    } }
    findingsReport?.let { report -> WasmSafeOverlay(onDismiss = { findingsReport = null }, errorMessage = errorMessage) {
        FindingsDialog(report = report, onDismiss = { findingsReport = null })
    } }
    reportToReview?.let { report -> WasmSafeOverlay(onDismiss = { reportToReview = null }, errorMessage = errorMessage) {
        ReviewReportDialog(
            report = report,
            onDismiss = { reportToReview = null },
            onReview = { action, reason ->
                scope.launch {
                    runCatching { OmsApiClient.reviewInspectionReport(report.report.uuid, action, reason) }
                        .onSuccess { reviewed ->
                            reports = reports.map { if (it.report.uuid == reviewed.uuid) it.copy(report = reviewed) else it }
                            reportToReview = null
                        }
                        .onFailure { errorMessage = LocalizationManager.t("error_review_report").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                }
            }
        )
    } }
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp, contentScrollState, -1)
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown, contentScrollState, 1)
    }
    }
    }
}

/** Read-only browser preview of the stored XLS/XLSX source. */
@Composable
private fun ReportPreviewDialog(report: ReportRow, onDismiss: () -> Unit) {
    var preview by remember(report.report.uuid) { mutableStateOf<ApiInspectionReportPreview?>(null) }
    var failed by remember(report.report.uuid) { mutableStateOf(false) }
    var selectedSheet by remember(report.report.uuid) { mutableStateOf(0) }
    val contentScroll = rememberScrollState()
    val tableScroll = rememberScrollState()
    LaunchedEffect(report.report.uuid) {
        failed = false
        runCatching { OmsApiClient.inspectionReportPreview(report.report.uuid) }
            .onSuccess { preview = it; selectedSheet = 0 }
            .onFailure { failed = true }
    }
    Card(
        Modifier.widthIn(max = 920.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 680.dp).verticalScroll(contentScroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(LocalizationManager.t("report_preview"), style = MaterialTheme.typography.titleLarge)
                    Text(report.title(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    preview?.let { Text(it.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TableActionIconButton(LocalizationManager.t("close"), Icons.Default.Close, onDismiss)
            }
            when {
                preview == null && !failed -> oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
                failed -> oms.components.ContentState(LocalizationManager.t("report_preview_failed"), error = true)
                else -> {
                    val loaded = requireNotNull(preview)
                    if (loaded.manual != null) {
                        ReadOnlySirReport(requireNotNull(loaded.manual))
                    } else if (loaded.sheets.isEmpty()) {
                        Text(LocalizationManager.t("report_preview_empty"))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            loaded.sheets.forEachIndexed { index, sheet ->
                                FilterChip(
                                    selected = selectedSheet == index,
                                    onClick = { selectedSheet = index },
                                    label = { Text(sheet.name) }
                                )
                            }
                        }
                        val sheet = loaded.sheets.getOrNull(selectedSheet) ?: loaded.sheets.first()
                        if (sheet.rows.isEmpty()) Text(LocalizationManager.t("report_preview_empty"))
                        else ReportWorkbookPreview(sheet, tableScroll)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("close")) }
            }
        }
    }
}

private data class PreviewSection(val title: String, val icon: ImageVector, val rows: IntRange)

/** Groups the standard SIR sheet exactly in the same reading order as its form. */
@Composable
private fun ReportWorkbookPreview(sheet: oms.data.ApiInspectionReportPreviewSheet, tableScroll: androidx.compose.foundation.ScrollState) {
    val isSir = sheet.name.equals("SIR", ignoreCase = true)
    val sections = if (isSir) listOf(
        PreviewSection(LocalizationManager.t("sir_report_header"), Icons.Default.Description, 1..5),
        PreviewSection(LocalizationManager.t("sir_personnel_weather"), Icons.Default.WbSunny, 6..10),
        PreviewSection(LocalizationManager.t("sir_ongoing_activities"), Icons.Default.Engineering, 11..26),
        PreviewSection(LocalizationManager.t("sir_ongoing_observations"), Icons.Default.FactCheck, 27..39),
        PreviewSection(LocalizationManager.t("sir_hse_observations"), Icons.Default.FactCheck, 40..46),
        PreviewSection(LocalizationManager.t("sir_quality_assessment"), Icons.Default.FactCheck, 47..55),
        PreviewSection(LocalizationManager.t("sir_inspector_section"), Icons.Default.Description, 56..Int.MAX_VALUE)
    ) else listOf(PreviewSection(sheet.name, Icons.Default.Description, 0..Int.MAX_VALUE))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEach { section ->
            val rows = sheet.rows.filter { it.rowNumber in section.rows }
            if (rows.isEmpty()) return@forEach
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    oms.components.FormSectionTitle(section.title, section.icon)
                    ReportWorkbookGrid(rows, tableScroll)
                }
            }
        }
    }
}

@Composable
private fun ReportWorkbookGrid(rows: List<oms.data.ApiInspectionReportPreviewRow>, scroll: androidx.compose.foundation.ScrollState) {
    val columns = rows.maxOfOrNull { it.cells.size } ?: 1
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
        Column(Modifier.width((42 + columns * 140).dp)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        row.rowNumber.toString(),
                        Modifier.width(42.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    (0 until columns).forEach { column ->
                        Text(
                            row.cells.getOrElse(column) { " " }.ifBlank { " " },
                            Modifier.width(140.dp).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(6.dp).heightIn(min = 34.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** The SIR template rendered in the same section order as the editable form. */
@Composable
internal fun ReadOnlySirReport(manual: ManualInspectionReportRequest) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ReadOnlySirSection(LocalizationManager.t("sir_header_site"), Icons.Default.Description) {
            PreviewField(LocalizationManager.t("sir_inspection_organisation"), manual.projectName)
            PreviewFieldRow(
                LocalizationManager.t("contractor") to manual.contractor,
                LocalizationManager.t("sir_site_reference") to manual.siteReference,
                LocalizationManager.t("date_label") to manual.inspectionDate.toOmsDate(),
                LocalizationManager.t("inspection_type") to LocalizationManager.t(manual.inspectionType)
            )
        }
        ReadOnlySirSection(LocalizationManager.t("sir_representatives"), Icons.Default.Engineering) {
            PreviewFieldRow(
                LocalizationManager.t("sir_contractor_representative") to manual.contractorRepresentative,
                LocalizationManager.t("sir_qa_staff") to manual.qaStaff,
                LocalizationManager.t("sir_usif_representative") to manual.usifRepresentative
            )
        }
        ReadOnlySirSection(LocalizationManager.t("sir_personnel_weather"), Icons.Default.WbSunny) {
            PreviewFieldRow(
                LocalizationManager.t("sir_skilled_labor") to manual.skilledLabor,
                LocalizationManager.t("sir_unskilled_labor") to manual.unskilledLabor,
                LocalizationManager.t("sir_site_management") to manual.siteManagement,
                LocalizationManager.t("sir_weather_conditions") to manual.weather
            )
        }
        ReadOnlySirSection(LocalizationManager.t("sir_ongoing_activities"), Icons.Default.Engineering) {
            if (manual.activities.isEmpty()) PreviewEmpty()
            manual.activities.forEach { activity ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewFieldRow(
                            LocalizationManager.t("sir_activity_location") to activity.location,
                            LocalizationManager.t("sir_activity_description") to activity.description,
                            LocalizationManager.t("sir_activity_remarks") to activity.remarks
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(LocalizationManager.t("sir_on_schedule"), style = MaterialTheme.typography.labelMedium)
                            oms.components.OmsBadge(
                                LocalizationManager.t(if (activity.onSchedule.equals("yes", true)) "yes" else "no"),
                                if (activity.onSchedule.equals("yes", true)) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }
            }
        }
        ReadOnlySirSection(LocalizationManager.t("sir_ongoing_observations"), Icons.Default.FactCheck) {
            if (manual.ongoingObservations.isEmpty()) PreviewEmpty()
            manual.ongoingObservations.forEach { observation -> Text("• $observation") }
        }
        ReadOnlySirSection(LocalizationManager.t("sir_hse_observations"), Icons.Default.FactCheck) {
            if (manual.hseObservations.isEmpty()) PreviewEmpty()
            manual.hseObservations.forEach { observation ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalizationManager.hseObservation(observation.observation), Modifier.weight(1f))
                        val yes = observation.answer.equals("yes", true)
                        oms.components.OmsBadge(LocalizationManager.t(if (yes) "yes" else "no"), if (yes) Color(0xFF2E7D32) else Color(0xFFC62828))
                        observation.comment?.takeIf(String::isNotBlank)?.let { Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        ReadOnlySirSection(LocalizationManager.t("sir_quality_assessment"), Icons.Default.FactCheck) {
            if (manual.qualityRemarks.isEmpty() && manual.progressComment.isNullOrBlank() && manual.scheduleRemark.isNullOrBlank()) PreviewEmpty()
            manual.qualityRemarks.forEach { remark ->
                PreviewFieldRow(
                    LocalizationManager.t("sir_quality_comment") to remark.comment,
                    LocalizationManager.t("sir_quality_rectification") to remark.rectification
                )
            }
            PreviewFieldRow(
                LocalizationManager.t("sir_progress_comments") to manual.progressComment,
                LocalizationManager.t("sir_schedule_remarks") to manual.scheduleRemark
            )
        }
        ReadOnlySirSection(LocalizationManager.t("sir_inspector_section"), Icons.Default.Description) {
            PreviewFieldRow(
                LocalizationManager.t("sir_name") to manual.inspectorName,
                LocalizationManager.t("sir_title_field") to manual.inspectorTitle
            )
        }
    }
}

@Composable
private fun ReadOnlySirSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            oms.components.FormSectionTitle(title, icon)
            content()
        }
    }
}

@Composable
private fun PreviewFieldRow(vararg fields: Pair<String, String?>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 800.dp) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fields.forEach { (label, value) -> PreviewField(label, value, Modifier.weight(1f)) }
        } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { (label, value) -> PreviewField(label, value, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun PreviewField(label: String, value: String?, modifier: Modifier = Modifier.fillMaxWidth()) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf(String::isNotBlank) ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PreviewEmpty() = Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ReportEditorDialog(
    report: ReportRow,
    canMoveReports: Boolean,
    canChangeStatus: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double?, Double?, String, String?) -> Unit
) {
    var date by remember(report.report.uuid) { mutableStateOf(report.report.inspectionDate) }
    var summary by remember(report.report.uuid) { mutableStateOf(report.report.summary.orEmpty()) }
    var inspectionType by remember(report.report.uuid) { mutableStateOf(report.report.inspectionType) }
    var status by remember(report.report.uuid) { mutableStateOf(report.report.status) }
    var latitude by remember(report.report.uuid) { mutableStateOf(report.report.latitude?.toString().orEmpty()) }
    var longitude by remember(report.report.uuid) { mutableStateOf(report.report.longitude?.toString().orEmpty()) }
    val projects = ProjectRepository.projects
    val projectsById = remember(projects) { projects.associateBy { it.id } }
    val initialAncestry = remember(report.report.uuid, projects) {
        projectsById[report.projectUuid]?.let { target ->
            generateSequence(target) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList().asReversed()
        }.orEmpty()
    }
    var selectedProjectUuid by remember(report.report.uuid, projects) { mutableStateOf(initialAncestry.getOrNull(0)?.id) }
    var selectedSubprojectUuid by remember(report.report.uuid, projects) { mutableStateOf(initialAncestry.getOrNull(1)?.id) }
    var selectedSubprojectPartUuid by remember(report.report.uuid, projects) { mutableStateOf(initialAncestry.getOrNull(2)?.id) }
    val targetProjectUuid = selectedSubprojectPartUuid ?: selectedSubprojectUuid ?: selectedProjectUuid ?: report.projectUuid
    val latitudeValue = latitude.replace(',', '.').toDoubleOrNull()
    val longitudeValue = longitude.replace(',', '.').toDoubleOrNull()
    val latitudeInvalid = latitude.isNotBlank() && (latitudeValue == null || latitudeValue !in -90.0..90.0)
    val longitudeInvalid = longitude.isNotBlank() && (longitudeValue == null || longitudeValue !in -180.0..180.0)
    val valid = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && !latitudeInvalid && !longitudeInvalid
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("edit_inspection"), style = MaterialTheme.typography.titleLarge)
            ReportLocationSelector(
                projects = projects,
                selectedProjectUuid = selectedProjectUuid,
                selectedSubprojectUuid = selectedSubprojectUuid,
                selectedSubprojectPartUuid = selectedSubprojectPartUuid,
                onProjectSelect = { selectedProjectUuid = it; selectedSubprojectUuid = null; selectedSubprojectPartUuid = null },
                onSubprojectSelect = { selectedSubprojectUuid = it; selectedSubprojectPartUuid = null },
                onSubprojectPartSelect = { selectedSubprojectPartUuid = it },
                enabled = canMoveReports
            )
            OmsDateField(date, { date = it }, LocalizationManager.t("date"), Modifier.fillMaxWidth(), true)
            InlineOptionPicker(
                options = listOf("planned", "unplanned", "final"),
                selected = inspectionType,
                prompt = LocalizationManager.t("inspection_type"),
                onSelect = { inspectionType = it },
                itemLabel = { LocalizationManager.t(it) },
                modifier = Modifier.fillMaxWidth()
            )
            if (canChangeStatus) {
                InlineOptionPicker(
                    options = listOf("draft", "pending_review", "completed"),
                    selected = status,
                    prompt = LocalizationManager.t("status"),
                    onSelect = { status = it },
                    itemLabel = { LocalizationManager.t("${it}_status") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it },
                label = { Text(LocalizationManager.t("report_title")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { value -> value.coordinateInputOrNull()?.let { latitude = it } },
                    label = { Text(LocalizationManager.t("latitude")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = latitudeInvalid
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { value -> value.coordinateInputOrNull()?.let { longitude = it } },
                    label = { Text(LocalizationManager.t("longitude")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = longitudeInvalid
                )
            }
            Text(LocalizationManager.t("attachments"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    openInspectionSourceReplacement(report.report.uuid) { }
                }) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(6.dp))
                    Text(LocalizationManager.t("replace_source_file"))
                }
                OutlinedButton(onClick = {
                    openInspectionPhotoUpload(report.report.uuid) { }
                }) {
                    Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(6.dp))
                    Text(LocalizationManager.t("upload_photo"))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(
                    onClick = {
                        onSave(
                            date, summary, inspectionType, latitudeValue, longitudeValue, targetProjectUuid,
                            status.takeIf { canChangeStatus }
                        )
                    },
                    enabled = valid
                ) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

/** Three-level placement picker for changing where an existing SIR belongs. */
@Composable
private fun ReportLocationSelector(
    projects: List<oms.model.Project>,
    selectedProjectUuid: String?,
    selectedSubprojectUuid: String?,
    selectedSubprojectPartUuid: String?,
    onProjectSelect: (String) -> Unit,
    onSubprojectSelect: (String) -> Unit,
    onSubprojectPartSelect: (String) -> Unit,
    enabled: Boolean
) {
    val rootProjects = projects.filter { it.projectType.equals("project", true) }
    val subprojects = projects.filter { it.projectType.equals("subproject", true) && it.parentProjectUuid == selectedProjectUuid }
    val parts = projects.filter { it.projectType.equals("subproject_part", true) && it.parentProjectUuid == selectedSubprojectUuid }
    fun label(project: oms.model.Project) = if (project.siteNumber.equals(project.localizedName(), true)) project.localizedName() else "${project.siteNumber} — ${project.localizedName()}"
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(LocalizationManager.t("report_location"), style = MaterialTheme.typography.titleSmall)
        InlineOptionPicker(
            rootProjects, rootProjects.firstOrNull { it.id == selectedProjectUuid }, LocalizationManager.t("select_project"),
            onSelect = { onProjectSelect(it.id) }, itemLabel = ::label, enabled = enabled
        )
        SearchableOptionPicker(
            subprojects, subprojects.firstOrNull { it.id == selectedSubprojectUuid }, LocalizationManager.t("select_subproject"),
            onSelect = { onSubprojectSelect(it.id) }, itemLabel = ::label, enabled = enabled && subprojects.isNotEmpty()
        )
        InlineOptionPicker(
            parts, parts.firstOrNull { it.id == selectedSubprojectPartUuid }, LocalizationManager.t("select_subproject_part"),
            onSelect = { onSubprojectPartSelect(it.id) }, itemLabel = ::label, enabled = enabled && parts.isNotEmpty()
        )
    }
}

@Composable
private fun ReviewReportDialog(
    report: ReportRow,
    onDismiss: () -> Unit,
    onReview: (action: String, rejectionReason: String?) -> Unit
) {
    var rejectionReason by remember(report.report.uuid) { mutableStateOf("") }
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("review_inspection_report"), style = MaterialTheme.typography.titleLarge)
            Text(report.report.summary ?: LocalizationManager.t("inspection_report"))
            OutlinedTextField(
                value = rejectionReason,
                onValueChange = { rejectionReason = it },
                label = { Text(LocalizationManager.t("revision_reason")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                OutlinedButton(
                    onClick = { onReview("reject", rejectionReason.trim()) },
                    enabled = rejectionReason.isNotBlank()
                ) { Text(LocalizationManager.t("return_for_revision")) }
                Button(onClick = { onReview("approve", null) }) { Text(LocalizationManager.t("approve")) }
            }
        }
    }
}

@Composable
private fun FindingsDialog(report: ReportRow, onDismiss: () -> Unit) {
    var findings by remember(report.report.uuid) { mutableStateOf<List<oms.data.ApiInspectionFinding>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<oms.data.ApiInspectionFinding?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { findings = runCatching { OmsApiClient.inspectionFindings(report.report.uuid) }.getOrElse { error = LocalizationManager.t("error_load_findings"); emptyList() } } }
    LaunchedEffect(report.report.uuid) { refresh() }

    if (adding || editing != null) {
        FindingEditorDialog(
            finding = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { category, severity, description, recommendation, isResolved ->
                scope.launch {
                    runCatching {
                        if (editing == null) OmsApiClient.createInspectionFinding(report.report.uuid, CreateInspectionFindingRequest(category, severity, description, recommendation))
                        else OmsApiClient.updateInspectionFinding(report.report.uuid, editing!!.uuid, UpdateInspectionFindingRequest(category, severity, description, recommendation, isResolved))
                    }.onSuccess { adding = false; editing = null; refresh() }
                        .onFailure { error = LocalizationManager.t("error_save_finding").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                }
            }
        )
    } else Card(Modifier.widthIn(max = 820.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LocalizationManager.t("inspection_findings"), style = MaterialTheme.typography.titleLarge)
                Text("${report.report.summary ?: LocalizationManager.t("inspection_report")} — ${report.projectName}", style = MaterialTheme.typography.bodySmall)
                if (findings.isEmpty()) Text(LocalizationManager.t("no_findings"))
                findings.forEach { finding ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${finding.category} • ${LocalizationManager.t("severity_${finding.severity.lowercase()}")}${if (finding.isResolved) " • ${LocalizationManager.t("resolved")}" else ""}", style = MaterialTheme.typography.titleSmall)
                            Text(finding.description)
                            finding.recommendation?.let { Text("${LocalizationManager.t("recommendation")}: $it", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editing = finding }) { Text(LocalizationManager.t("edit")) }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { OmsApiClient.updateInspectionFinding(report.report.uuid, finding.uuid, UpdateInspectionFindingRequest(finding.category, finding.severity, finding.description, finding.recommendation, !finding.isResolved)) }
                                            .onSuccess { refresh() }.onFailure { error = LocalizationManager.t("error_update_finding") }
                                    }
                                }) { Text(if (finding.isResolved) LocalizationManager.t("reopen") else LocalizationManager.t("resolve")) }
                                TextButton(onClick = {
                                    scope.launch {
                                        if (OmsApiClient.deleteInspectionFinding(report.report.uuid, finding.uuid)) refresh()
                                        else error = LocalizationManager.t("error_delete_finding")
                                    }
                                }) { Text(LocalizationManager.t("delete")) }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("close")) }
                Button(onClick = { adding = true }) { Text(LocalizationManager.t("add_finding")) }
            }
        }
    }
}

@Composable
private fun FindingEditorDialog(
    finding: oms.data.ApiInspectionFinding?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, Boolean) -> Unit
) {
    var category by remember(finding?.uuid) { mutableStateOf(finding?.category ?: "") }
    var severity by remember(finding?.uuid) { mutableStateOf(finding?.severity ?: "medium") }
    var description by remember(finding?.uuid) { mutableStateOf(finding?.description ?: "") }
    var recommendation by remember(finding?.uuid) { mutableStateOf(finding?.recommendation ?: "") }
    var isResolved by remember(finding?.uuid) { mutableStateOf(finding?.isResolved ?: false) }
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (finding == null) LocalizationManager.t("add_finding") else LocalizationManager.t("edit_finding"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(category, { category = it.safePastedText(200) }, label = { Text(LocalizationManager.t("category")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "critical").forEach { value -> FilterChip(selected = severity == value, onClick = { severity = value }, label = { Text(LocalizationManager.t("severity_$value")) }) }
                }
                OutlinedTextField(description, { description = it.safePastedText(8_000) }, label = { Text(LocalizationManager.t("description")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(recommendation, { recommendation = it.safePastedText(8_000) }, label = { Text(LocalizationManager.t("recommendation")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (finding != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isResolved, { isResolved = it }); Text(LocalizationManager.t("resolved")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onSave(category, severity, description, recommendation.ifBlank { null }, isResolved) }, enabled = category.isNotBlank() && description.isNotBlank()) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

private fun String.safePastedText(maxLength: Int): String =
    replace("\u0000", "").take(maxLength)

@Composable
private fun ReportTableHeader(
    sort: ReportSort,
    ascending: Boolean,
    onSort: (ReportSort) -> Unit,
    reports: List<ReportRow>,
    subprojectFilter: String?,
    onSubprojectFilterChange: (String?) -> Unit,
    subprojectCodeFilter: String?,
    onSubprojectCodeFilterChange: (String?) -> Unit,
    subprojectPartCodeFilter: String?,
    onSubprojectPartCodeFilterChange: (String?) -> Unit,
    statusFilter: String?,
    onStatusFilterChange: (String?) -> Unit,
    authorFilter: String?,
    onAuthorFilterChange: (String?) -> Unit
) {
    val subprojects = reports
        .asSequence()
        .mapNotNull { it.localizedSubprojectName() }
        .distinct()
        .sorted()
        .toList()
    val partCodes = reports
        .asSequence()
        .filter { subprojectFilter == null || it.localizedSubprojectName() == subprojectFilter }
        .mapNotNull { it.subprojectPartCode }
        .distinct()
        .sorted()
        .toList()
    val subprojectCodes = reports.mapNotNull { it.subprojectCode }.distinct().sorted()
    val authors = reports.mapNotNull { it.report.authorUsername?.takeIf(String::isNotBlank) }.distinct().sorted()
    val statuses = listOf("draft", "pending_review", "completed")

    Column(Modifier.width(1_163.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(105.dp).padding(top = 14.dp)) {
                Text(LocalizationManager.t("filters"), style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.width(220.dp)) {
                FilterDropdown(LocalizationManager.t("subproject"), subprojects, subprojectFilter, onSubprojectFilterChange, Modifier.fillMaxWidth()) { it }
            }
            Box(Modifier.width(150.dp)) {
                FilterDropdown(LocalizationManager.t("subproject_code"), subprojectCodes, subprojectCodeFilter, onSubprojectCodeFilterChange, Modifier.fillMaxWidth()) { it }
            }
            Box(Modifier.width(160.dp)) {
                FilterDropdown(LocalizationManager.t("subproject_part_code_label"), partCodes, subprojectPartCodeFilter, onSubprojectPartCodeFilterChange, Modifier.fillMaxWidth()) { it }
            }
            Box(Modifier.width(130.dp)) {
                FilterDropdown(
                    LocalizationManager.t("status"),
                    statuses,
                    statusFilter,
                    onStatusFilterChange,
                    Modifier.fillMaxWidth()
                ) { LocalizationManager.t("${it}_status") }
            }
            Box(Modifier.width(110.dp)) {
                FilterDropdown(LocalizationManager.t("uploaded_by_short"), authors, authorFilter, onAuthorFilterChange, Modifier.fillMaxWidth()) { it }
            }
            Spacer(Modifier.width(288.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            SortableTableHeader(LocalizationManager.t("date"), sort == ReportSort.Date, ascending, { onSort(ReportSort.Date) }, Modifier.width(105.dp))
            SortableTableHeader(LocalizationManager.t("subproject"), sort == ReportSort.Subproject, ascending, { onSort(ReportSort.Subproject) }, Modifier.width(220.dp))
            SortableTableHeader(LocalizationManager.t("subproject_code"), sort == ReportSort.SubprojectCode, ascending, { onSort(ReportSort.SubprojectCode) }, Modifier.width(150.dp))
            SortableTableHeader(LocalizationManager.t("subproject_part_code_label"), sort == ReportSort.SubprojectPartCode, ascending, { onSort(ReportSort.SubprojectPartCode) }, Modifier.width(160.dp))
            SortableTableHeader(LocalizationManager.t("status"), sort == ReportSort.Status, ascending, { onSort(ReportSort.Status) }, Modifier.width(130.dp))
            SortableTableHeader(LocalizationManager.t("uploaded_by_short"), sort == ReportSort.Author, ascending, { onSort(ReportSort.Author) }, Modifier.width(110.dp))
            Box(Modifier.width(288.dp).height(52.dp), contentAlignment = Alignment.Center) {
                Text(LocalizationManager.t("actions"))
            }
        }
    }
}
