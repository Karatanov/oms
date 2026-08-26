package oms.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.data.ApiInspectionReport
import oms.data.CreateInspectionFindingRequest
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.data.UpdateInspectionFindingRequest
import oms.data.ApiDashboard
import oms.components.SortableTableHeader
import oms.components.ReportStatusChip
import oms.components.TableActionIconButton
import oms.components.FilterDropdown
import oms.components.InlineOptionPicker
import oms.components.OmsDateField
import oms.components.toOmsDate
import oms.components.WasmSafeOverlay
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("openInspectionPhotoUpload")
external fun openInspectionPhotoUpload(reportUuid: String)

private data class ReportRow(
    val projectUuid: String,
    val projectName: String,
    val subprojectName: String?,
    val subprojectPartCode: String?,
    val report: ApiInspectionReport
)
private enum class ReportSort { Date, ReportTitle, Project, Subproject, SubprojectPartCode, Status, Author }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReportsScreen(
    onNewInspection: () -> Unit = {},
    canCreateReports: Boolean = true,
    canReviewReports: Boolean = true,
    canMoveReports: Boolean = true
) {
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var subprojectFilter by remember { mutableStateOf<String?>(null) }
    var subprojectPartCodeFilter by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportToMove by remember { mutableStateOf<ReportRow?>(null) }
    var reportToEdit by remember { mutableStateOf<ReportRow?>(null) }
    var findingsReport by remember { mutableStateOf<ReportRow?>(null) }
    var reportToReview by remember { mutableStateOf<ReportRow?>(null) }
    var isMovingReport by remember { mutableStateOf(false) }
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(ReportSort.Date) }
    var ascending by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val contentScrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        val reportItems = coroutineScope {
            val refreshProjects = async { ProjectRepository.refresh() }
            val loadReports = async { OmsApiClient.inspectionReports() }
            val loadDashboard = async { runCatching { OmsApiClient.dashboard() }.getOrNull() }
            refreshProjects.await()
            dashboard = loadDashboard.await()
            loadReports.await()
        }
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        reports = reportItems.mapNotNull { item ->
            val attachedProject = projectsById[item.projectUuid] ?: return@mapNotNull null
            val ancestry = generateSequence(attachedProject) { current ->
                current.parentProjectUuid?.let(projectsById::get)
            }.toList().asReversed()
            val root = ancestry.firstOrNull() ?: attachedProject
            val projectName = root.name
            val subprojectName = ancestry.getOrNull(1)?.name
            val subprojectPartCode = ancestry.getOrNull(2)?.siteNumber
            ReportRow(attachedProject.id, projectName, subprojectName, subprojectPartCode, item.report)
        }.sortedByDescending { it.report.inspectionDate }
    }
    val visible = remember(reports, status, projectFilter, subprojectFilter, subprojectPartCodeFilter, sort, ascending) {
        reports.asSequence().filter {
                (status == null || it.report.status == status) &&
                (projectFilter == null || it.projectName == projectFilter) &&
                (subprojectFilter == null || it.subprojectName == subprojectFilter) &&
                (subprojectPartCodeFilter == null || it.subprojectPartCode == subprojectPartCodeFilter)
        }.sortedWith(
            compareBy<ReportRow> {
                when (sort) {
                    ReportSort.Date -> it.report.inspectionDate
                    ReportSort.ReportTitle -> it.report.summary.orEmpty()
                    ReportSort.Project -> it.projectName
                    ReportSort.Subproject -> it.subprojectName.orEmpty()
                    ReportSort.SubprojectPartCode -> it.subprojectPartCode.orEmpty()
                    ReportSort.Status -> it.report.status
                    ReportSort.Author -> "admin"
                }
            }.let { if (ascending) it else it.reversed() }
        ).toList()
    }
    fun selectSort(column: ReportSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    fun scrollBy(delta: Float) = scope.launch { contentScrollState.animateScrollBy(delta) }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalizationManager.t("reports_title"), style = MaterialTheme.typography.headlineMedium)
            if (canCreateReports) Button(onClick = onNewInspection) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(LocalizationManager.t("new_inspection")) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { MetricsChart("inspections_by_month", "inspections_by_month_hint", dashboard?.monthlyInspectionCounts.orEmpty()) }
            Box(Modifier.weight(1f)) { MetricsChart("eshs_violations_by_month", "eshs_violations_by_month_hint", dashboard?.monthlyEshsViolations.orEmpty()) }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTableHeader(
                    sort = sort,
                    ascending = ascending,
                    onSort = ::selectSort,
                    reports = reports,
                    projectFilter = projectFilter,
                    onProjectFilterChange = { projectFilter = it; subprojectFilter = null; subprojectPartCodeFilter = null },
                    subprojectFilter = subprojectFilter,
                    onSubprojectFilterChange = { subprojectFilter = it; subprojectPartCodeFilter = null },
                    subprojectPartCodeFilter = subprojectPartCodeFilter,
                    onSubprojectPartCodeFilterChange = { subprojectPartCodeFilter = it },
                    statusFilter = status,
                    onStatusFilterChange = { status = it }
                )
                HorizontalDivider()
                if (visible.isEmpty()) Text(LocalizationManager.t("no_reports"))
                visible.forEach { row ->
                    Row(Modifier.width(1_575.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.report.inspectionDate.toOmsDate(), Modifier.width(105.dp))
                        Text(row.report.summary ?: LocalizationManager.t("inspection_report"), Modifier.width(400.dp), style = MaterialTheme.typography.bodyMedium)
                        Text(row.projectName, Modifier.width(180.dp), style = MaterialTheme.typography.bodySmall)
                        Text(row.subprojectName ?: "—", Modifier.width(180.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(row.subprojectPartCode ?: "—", Modifier.width(160.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.width(130.dp)) { ReportStatusChip(row.report.status) }
                        Text("admin", Modifier.width(80.dp))
                        if (canCreateReports) TableActionIconButton(LocalizationManager.t("edit_inspection"), Icons.Default.Edit) { reportToEdit = row }
                        else Spacer(Modifier.width(48.dp))
                        if (canReviewReports && row.report.status == "pending_review") {
                            TableActionIconButton(LocalizationManager.t("review_report"), Icons.Default.RateReview) { reportToReview = row }
                        } else {
                            Spacer(Modifier.width(48.dp))
                        }
                        TableActionIconButton(LocalizationManager.t("open_source_file"), Icons.Default.FileDownload) {
                            uriHandler.openUri(oms.data.omsApiUrl("/inspection-reports/${row.report.uuid}/source-file"))
                        }
                        if (canCreateReports) {
                            TableActionIconButton(LocalizationManager.t("upload_photo"), Icons.Default.PhotoCamera) { openInspectionPhotoUpload(row.report.uuid) }
                            TableActionIconButton(LocalizationManager.t("findings"), Icons.AutoMirrored.Filled.FactCheck) { findingsReport = row }
                        } else Spacer(Modifier.width(96.dp))
                        if (canMoveReports) TableActionIconButton(LocalizationManager.t("move_report"), Icons.Default.SwapHoriz) { reportToMove = row }
                        else Spacer(Modifier.width(48.dp))
                        if (canCreateReports) TableActionIconButton(LocalizationManager.t("delete_report"), Icons.Default.Delete) {
                            scope.launch {
                                if (OmsApiClient.deleteInspectionReport(row.report.uuid)) reports = reports.filterNot { it.report.uuid == row.report.uuid }
                                else errorMessage = LocalizationManager.t("error_delete_report")
                            }
                        } else Spacer(Modifier.width(48.dp))
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    reportToEdit?.let { report -> WasmSafeOverlay {
        ReportEditorDialog(
            report = report,
            onDismiss = { reportToEdit = null },
            onSave = { date, summary, reportCode, inspectionType, latitude, longitude ->
                scope.launch {
                    runCatching {
                        OmsApiClient.updateInspectionReport(
                            report.report.uuid, date, summary, reportCode, inspectionType, latitude, longitude
                        )
                    }
                        .onSuccess { updated ->
                            reports = reports.map { if (it.report.uuid == updated.uuid) it.copy(report = updated) else it }
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
    reportToMove?.let { report -> WasmSafeOverlay {
        MoveReportDialog(
            report = report,
            onDismiss = { reportToMove = null },
            isMoving = isMovingReport,
            onMove = { target ->
                isMovingReport = true
                scope.launch {
                    runCatching { OmsApiClient.moveInspectionReport(report.report.uuid, target.id) }
                        .onSuccess { moved ->
                            if (!moved) {
                                errorMessage = LocalizationManager.t("error_move_report")
                                return@onSuccess
                            }
                            reports = reports.map {
                                if (it.report.uuid == report.report.uuid) {
                                    val ancestry = generateSequence(target) { current -> current.parentProjectUuid?.let { ProjectRepository.projects.firstOrNull { project -> project.id == it } } }.toList().asReversed()
                                    it.copy(
                                        projectUuid = target.id,
                                        projectName = ancestry.firstOrNull()?.name ?: target.name,
                                        subprojectName = ancestry.getOrNull(1)?.name,
                                        subprojectPartCode = ancestry.getOrNull(2)?.siteNumber
                                    )
                                } else it
                            }
                            reportToMove = null
                        }
                        .onFailure { errorMessage = LocalizationManager.t("error_move_report_detail").replace("{message}", it.message ?: LocalizationManager.t("unknown_error")) }
                    isMovingReport = false
                }
            }
        )
    } }
    findingsReport?.let { report -> WasmSafeOverlay {
        FindingsDialog(report = report, onDismiss = { findingsReport = null })
    } }
    reportToReview?.let { report -> WasmSafeOverlay {
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
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(LocalizationManager.t("dashboard_scroll_up")) } }, state = rememberTooltipState()) {
            FilledIconButton(onClick = { scrollBy(-420f) }) { Icon(Icons.Default.KeyboardArrowUp, LocalizationManager.t("dashboard_scroll_up")) }
        }
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(LocalizationManager.t("dashboard_scroll_down")) } }, state = rememberTooltipState()) {
            FilledIconButton(onClick = { scrollBy(420f) }) { Icon(Icons.Default.KeyboardArrowDown, LocalizationManager.t("dashboard_scroll_down")) }
        }
    }
    }
}

@Composable
private fun ReportEditorDialog(
    report: ReportRow,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, Double?, Double?) -> Unit
) {
    var date by remember(report.report.uuid) { mutableStateOf(report.report.inspectionDate) }
    var summary by remember(report.report.uuid) { mutableStateOf(report.report.summary.orEmpty()) }
    var reportCode by remember(report.report.uuid) { mutableStateOf(report.report.reportCode.orEmpty()) }
    var inspectionType by remember(report.report.uuid) { mutableStateOf(report.report.inspectionType) }
    var latitude by remember(report.report.uuid) { mutableStateOf(report.report.latitude?.toString().orEmpty()) }
    var longitude by remember(report.report.uuid) { mutableStateOf(report.report.longitude?.toString().orEmpty()) }
    val latitudeValue = latitude.replace(',', '.').toDoubleOrNull()
    val longitudeValue = longitude.replace(',', '.').toDoubleOrNull()
    val latitudeInvalid = latitude.isNotBlank() && (latitudeValue == null || latitudeValue !in -90.0..90.0)
    val longitudeInvalid = longitude.isNotBlank() && (longitudeValue == null || longitudeValue !in -180.0..180.0)
    val valid = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && !latitudeInvalid && !longitudeInvalid
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("edit_inspection"), style = MaterialTheme.typography.titleLarge)
            OmsDateField(date, { date = it }, LocalizationManager.t("date"), Modifier.fillMaxWidth(), true)
            OutlinedTextField(
                value = reportCode,
                onValueChange = { reportCode = it },
                label = { Text(LocalizationManager.t("inspection_code")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            InlineOptionPicker(
                options = listOf("planned", "unplanned", "final"),
                selected = inspectionType,
                prompt = LocalizationManager.t("inspection_type"),
                onSelect = { inspectionType = it },
                itemLabel = { LocalizationManager.t(it) },
                modifier = Modifier.fillMaxWidth()
            )
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
                    onValueChange = { value -> if (value.matches(Regex("-?[0-9.,]*"))) latitude = value },
                    label = { Text(LocalizationManager.t("latitude")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = latitudeInvalid
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { value -> if (value.matches(Regex("-?[0-9.,]*"))) longitude = value },
                    label = { Text(LocalizationManager.t("longitude")) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = longitudeInvalid
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(
                    onClick = {
                        onSave(
                            date, summary, reportCode.trim().ifBlank { null }, inspectionType,
                            latitudeValue, longitudeValue
                        )
                    },
                    enabled = valid
                ) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

@Composable
private fun ReviewReportDialog(
    report: ReportRow,
    onDismiss: () -> Unit,
    onReview: (action: String, rejectionReason: String?) -> Unit
) {
    var rejectionReason by remember(report.report.uuid) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
    } else Card(Modifier.fillMaxWidth().widthIn(max = 820.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(LocalizationManager.t("inspection_findings"), style = MaterialTheme.typography.titleLarge)
                Text("${report.report.summary ?: LocalizationManager.t("inspection_report")} — ${report.projectName}", style = MaterialTheme.typography.bodySmall)
                if (findings.isEmpty()) Text(LocalizationManager.t("no_findings"))
                findings.forEach { finding ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${finding.category} • ${finding.severity.uppercase()}${if (finding.isResolved) " • RESOLVED" else ""}", style = MaterialTheme.typography.titleSmall)
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
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (finding == null) LocalizationManager.t("add_finding") else LocalizationManager.t("edit_finding"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(category, { category = it }, label = { Text(LocalizationManager.t("category")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "critical").forEach { value -> FilterChip(selected = severity == value, onClick = { severity = value }, label = { Text(LocalizationManager.t("severity_$value")) }) }
                }
                OutlinedTextField(description, { description = it }, label = { Text(LocalizationManager.t("description")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(recommendation, { recommendation = it }, label = { Text(LocalizationManager.t("recommendation")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (finding != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isResolved, { isResolved = it }); Text(LocalizationManager.t("resolved")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onSave(category, severity, description, recommendation.ifBlank { null }, isResolved) }, enabled = category.isNotBlank() && description.isNotBlank()) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

@Composable
private fun MoveReportDialog(
    report: ReportRow,
    onDismiss: () -> Unit,
    isMoving: Boolean,
    onMove: (oms.model.Project) -> Unit
) {
    var selectedUuid by remember(report.report.uuid) { mutableStateOf<String?>(null) }
    val projects = ProjectRepository.projects.filter { it.id != report.projectUuid }
    val selected = projects.firstOrNull { it.id == selectedUuid }
    // Compose/Wasm AlertDialog can leave a popup focus layer active when an inline
    // selector changes its state.  Keep this editor in the page layout instead.
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("move_report"), style = MaterialTheme.typography.titleLarge)
            Text(report.report.summary ?: LocalizationManager.t("inspection_report"), style = MaterialTheme.typography.bodyMedium)
            InlineOptionPicker(
                options = projects,
                selected = selected,
                prompt = LocalizationManager.t("select_target_project"),
                onSelect = { selectedUuid = it.id },
                itemLabel = { "${it.name} (${it.region})" },
                enabled = !isMoving
            )
            if (projects.isEmpty()) Text(LocalizationManager.t("no_other_project"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss, enabled = !isMoving) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { selected?.let(onMove) }, enabled = selected != null && !isMoving) {
                    if (isMoving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(LocalizationManager.t("move"))
                }
            }
        }
    }
}

@Composable
private fun ReportTableHeader(
    sort: ReportSort,
    ascending: Boolean,
    onSort: (ReportSort) -> Unit,
    reports: List<ReportRow>,
    projectFilter: String?,
    onProjectFilterChange: (String?) -> Unit,
    subprojectFilter: String?,
    onSubprojectFilterChange: (String?) -> Unit,
    subprojectPartCodeFilter: String?,
    onSubprojectPartCodeFilterChange: (String?) -> Unit,
    statusFilter: String?,
    onStatusFilterChange: (String?) -> Unit
) {
    val subprojects = reports
        .asSequence()
        .filter { projectFilter == null || it.projectName == projectFilter }
        .mapNotNull { it.subprojectName }
        .distinct()
        .sorted()
        .toList()
    val partCodes = reports
        .asSequence()
        .filter { projectFilter == null || it.projectName == projectFilter }
        .filter { subprojectFilter == null || it.subprojectName == subprojectFilter }
        .mapNotNull { it.subprojectPartCode }
        .distinct()
        .sorted()
        .toList()
    val statuses = listOf("draft", "pending_review", "completed")

    Column(Modifier.width(1_575.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.width(105.dp).padding(top = 14.dp)) {
                Text(LocalizationManager.t("filters"), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(400.dp))
            Box(Modifier.width(180.dp)) {
                FilterDropdown(LocalizationManager.t("project"), reports.map { it.projectName }.distinct().sorted(), projectFilter, onProjectFilterChange, Modifier.fillMaxWidth()) { it }
            }
            Box(Modifier.width(180.dp)) {
                FilterDropdown(LocalizationManager.t("subproject"), subprojects, subprojectFilter, onSubprojectFilterChange, Modifier.fillMaxWidth()) { it }
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
            Spacer(Modifier.width(80.dp + 336.dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            SortableTableHeader(LocalizationManager.t("date"), sort == ReportSort.Date, ascending, { onSort(ReportSort.Date) }, Modifier.width(105.dp))
            SortableTableHeader(LocalizationManager.t("report_title"), sort == ReportSort.ReportTitle, ascending, { onSort(ReportSort.ReportTitle) }, Modifier.width(400.dp))
            SortableTableHeader(LocalizationManager.t("project"), sort == ReportSort.Project, ascending, { onSort(ReportSort.Project) }, Modifier.width(180.dp))
            SortableTableHeader(LocalizationManager.t("subproject"), sort == ReportSort.Subproject, ascending, { onSort(ReportSort.Subproject) }, Modifier.width(180.dp))
            SortableTableHeader(LocalizationManager.t("subproject_part_code_label"), sort == ReportSort.SubprojectPartCode, ascending, { onSort(ReportSort.SubprojectPartCode) }, Modifier.width(160.dp))
            SortableTableHeader(LocalizationManager.t("status"), sort == ReportSort.Status, ascending, { onSort(ReportSort.Status) }, Modifier.width(130.dp))
            SortableTableHeader(LocalizationManager.t("uploaded_by_short"), sort == ReportSort.Author, ascending, { onSort(ReportSort.Author) }, Modifier.width(80.dp))
            Box(Modifier.width(336.dp).height(48.dp), contentAlignment = Alignment.Center) {
                Text(LocalizationManager.t("actions"))
            }
        }
    }
}
