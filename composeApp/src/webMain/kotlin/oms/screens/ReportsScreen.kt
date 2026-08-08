package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.ApiInspectionReport
import oms.data.CreateInspectionFindingRequest
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.data.UpdateInspectionFindingRequest
import oms.components.SortableTableHeader
import oms.components.ReportStatusChip
import oms.components.TableActionIconButton
import oms.components.FilterDropdown
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("openInspectionPhotoUpload")
external fun openInspectionPhotoUpload(reportUuid: String)

private data class ReportRow(
    val projectUuid: String,
    val projectName: String,
    val subprojectName: String?,
    val report: ApiInspectionReport
)
private enum class ReportSort { Date, ReportTitle, Project, Subproject, Status, Author }

@Composable
fun ReportsScreen(
    onNewInspection: () -> Unit = {},
    canReviewReports: Boolean = true,
    canMoveReports: Boolean = true
) {
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var subprojectFilter by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportToMove by remember { mutableStateOf<ReportRow?>(null) }
    var findingsReport by remember { mutableStateOf<ReportRow?>(null) }
    var reportToReview by remember { mutableStateOf<ReportRow?>(null) }
    var reportToView by remember { mutableStateOf<ReportRow?>(null) }
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(ReportSort.Date) }
    var ascending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        reports = ProjectRepository.projects.flatMap { attachedProject ->
            val parent = attachedProject.parentProjectUuid?.let(projectsById::get)
            val projectName = parent?.name ?: attachedProject.name
            val subprojectName = if (parent == null) null else attachedProject.name
            runCatching { OmsApiClient.projectReports(attachedProject.id) }.getOrDefault(emptyList())
                .map { ReportRow(attachedProject.id, projectName, subprojectName, it) }
        }.sortedByDescending { it.report.inspectionDate }
    }
    val visible = reports.filter {
        (status == null || it.report.status == status) &&
            (projectFilter == null || it.projectName == projectFilter) &&
            (subprojectFilter == null || it.subprojectName == subprojectFilter)
    }.sortedWith(
        compareBy<ReportRow> {
            when (sort) {
                ReportSort.Date -> it.report.inspectionDate
                ReportSort.ReportTitle -> it.report.summary.orEmpty()
                ReportSort.Project -> it.projectName
                ReportSort.Subproject -> it.subprojectName.orEmpty()
                ReportSort.Status -> it.report.status
                ReportSort.Author -> "admin"
            }
        }.let { if (ascending) it else it.reversed() }
    )
    fun selectSort(column: ReportSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(LocalizationManager.t("reports_title"), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onNewInspection) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(LocalizationManager.t("new_inspection")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, "draft", "pending_review", "completed").forEach { value ->
                FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value?.let { LocalizationManager.t("${it}_status") } ?: LocalizationManager.t("all")) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown("Проєкт", reports.map { it.projectName }.distinct().sorted(), projectFilter, { projectFilter = it; subprojectFilter = null }) { it }
            FilterDropdown("Субпроєкт", reports.filter { projectFilter == null || it.projectName == projectFilter }.mapNotNull { it.subprojectName }.distinct().sorted(), subprojectFilter, { subprojectFilter = it }) { it }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visible.isEmpty()) Text(LocalizationManager.t("no_reports"))
                visible.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.report.inspectionDate, Modifier.width(105.dp))
                        Text(row.report.summary ?: LocalizationManager.t("inspection_report"), Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
                        Text(row.projectName, Modifier.width(190.dp), style = MaterialTheme.typography.bodySmall)
                        Text(row.subprojectName ?: "—", Modifier.width(190.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.width(130.dp)) { ReportStatusChip(row.report.status) }
                        Text("admin", Modifier.width(100.dp))
                        TableActionIconButton(LocalizationManager.t("view"), Icons.Default.Visibility) { reportToView = row }
                        if (canReviewReports && row.report.status == "pending_review") {
                            TableActionIconButton("Review report", Icons.Default.RateReview) { reportToReview = row }
                        }
                        TableActionIconButton(LocalizationManager.t("upload_photo"), Icons.Default.PhotoCamera) { openInspectionPhotoUpload(row.report.uuid) }
                        TableActionIconButton(LocalizationManager.t("findings"), Icons.AutoMirrored.Filled.FactCheck) { findingsReport = row }
                        if (canMoveReports) TableActionIconButton(LocalizationManager.t("move_report"), Icons.Default.SwapHoriz) { reportToMove = row }
                        TableActionIconButton(LocalizationManager.t("delete_report"), Icons.Default.Delete) {
                            scope.launch {
                                if (OmsApiClient.deleteInspectionReport(row.report.uuid)) reports = reports.filterNot { it.report.uuid == row.report.uuid }
                                else errorMessage = "Could not delete report."
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        reportToMove?.let { report ->
            MoveReportDialog(
                report = report,
                onDismiss = { reportToMove = null },
                onMove = { target ->
                    scope.launch {
                        if (OmsApiClient.moveInspectionReport(report.report.uuid, target.id)) {
                            reports = reports.map {
                                if (it.report.uuid == report.report.uuid) {
                                    val parent = target.parentProjectUuid?.let { parentId -> ProjectRepository.projects.firstOrNull { project -> project.id == parentId } }
                                    it.copy(projectUuid = target.id, projectName = parent?.name ?: target.name, subprojectName = parent?.let { target.name })
                                } else it
                            }
                            reportToMove = null
                        } else errorMessage = "Could not move report."
                    }
                }
            )
        }
        findingsReport?.let { report ->
            FindingsDialog(report = report, onDismiss = { findingsReport = null })
        }
        reportToReview?.let { report ->
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
                            .onFailure { errorMessage = "Could not review report: ${it.message ?: "unknown error"}" }
                    }
                }
            )
        }
        reportToView?.let { report ->
            ReportViewerDialog(report = report, onDismiss = { reportToView = null })
        }
    }
}

@Composable
private fun ReportViewerDialog(report: ReportRow, onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var findings by remember(report.report.uuid) { mutableStateOf<List<oms.data.ApiInspectionFinding>>(emptyList()) }
    var photos by remember(report.report.uuid) { mutableStateOf<List<oms.data.ApiInspectionPhoto>>(emptyList()) }
    LaunchedEffect(report.report.uuid) {
        findings = runCatching { OmsApiClient.inspectionFindings(report.report.uuid) }.getOrDefault(emptyList())
        photos = runCatching { OmsApiClient.inspectionPhotos(report.report.uuid) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.report.summary ?: LocalizationManager.t("inspection_report")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${LocalizationManager.t("report_project")}: ${report.projectName}")
                Text("${LocalizationManager.t("date")}: ${report.report.inspectionDate}")
                Text("${LocalizationManager.t("status")}: ${report.report.status.replace('_', ' ')}")
                report.report.rejectionReason?.let { Text("Причина повернення: $it") }
                Text("${LocalizationManager.t("inspection_findings")}: ${findings.size}")
                findings.forEach { finding ->
                    Text("• ${finding.category}: ${finding.description}", style = MaterialTheme.typography.bodySmall)
                }
                Text("${LocalizationManager.t("photos")}: ${photos.size}")
            }
        },
        confirmButton = {
            Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/inspection-reports/${report.report.uuid}/source-file") }) {
                Text("Завантажити XLSX")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(LocalizationManager.t("close")) } }
    )
}

@Composable
private fun ReviewReportDialog(
    report: ReportRow,
    onDismiss: () -> Unit,
    onReview: (action: String, rejectionReason: String?) -> Unit
) {
    var rejectionReason by remember(report.report.uuid) { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Review inspection report", style = MaterialTheme.typography.titleLarge)
            Text(report.report.summary ?: LocalizationManager.t("inspection_report"))
            OutlinedTextField(
                value = rejectionReason,
                onValueChange = { rejectionReason = it },
                label = { Text("Reason for returning for revision") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                OutlinedButton(
                    onClick = { onReview("reject", rejectionReason.trim()) },
                    enabled = rejectionReason.isNotBlank()
                ) { Text("Return for revision") }
                Button(onClick = { onReview("approve", null) }) { Text("Approve") }
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
    fun refresh() { scope.launch { findings = runCatching { OmsApiClient.inspectionFindings(report.report.uuid) }.getOrElse { error = "Could not load findings."; emptyList() } } }
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
                        .onFailure { error = "Could not save finding: ${it.message ?: "unknown error"}" }
                }
            }
        )
    } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            .onSuccess { refresh() }.onFailure { error = "Could not update finding." }
                                    }
                                }) { Text(if (finding.isResolved) LocalizationManager.t("reopen") else LocalizationManager.t("resolve")) }
                                TextButton(onClick = {
                                    scope.launch {
                                        if (OmsApiClient.deleteInspectionFinding(report.report.uuid, finding.uuid)) refresh()
                                        else error = "Could not delete finding."
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (finding == null) LocalizationManager.t("add_finding") else LocalizationManager.t("edit_finding"), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(category, { category = it }, label = { Text(LocalizationManager.t("category")) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "critical").forEach { value -> FilterChip(selected = severity == value, onClick = { severity = value }, label = { Text(value) }) }
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
private fun MoveReportDialog(report: ReportRow, onDismiss: () -> Unit, onMove: (oms.model.Project) -> Unit) {
    var selectedUuid by remember(report.report.uuid) { mutableStateOf<String?>(null) }
    val projects = ProjectRepository.projects.filter { it.id != report.projectUuid }
    val selected = projects.firstOrNull { it.id == selectedUuid }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("move_report"), style = MaterialTheme.typography.titleLarge)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.name ?: LocalizationManager.t("select_target_project"))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text("${project.name} (${project.region})") },
                                onClick = { selectedUuid = project.id; expanded = false }
                            )
                        }
                    }
                }
                if (projects.isEmpty()) Text(LocalizationManager.t("no_other_project"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { selected?.let(onMove) }, enabled = selected != null) { Text(LocalizationManager.t("move")) }
            }
        }
    }
}

@Composable
private fun ReportTableHeader(sort: ReportSort, ascending: Boolean, onSort: (ReportSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("date"), sort == ReportSort.Date, ascending, { onSort(ReportSort.Date) }, Modifier.width(105.dp))
        SortableTableHeader("Назва звіту", sort == ReportSort.ReportTitle, ascending, { onSort(ReportSort.ReportTitle) }, Modifier.weight(1.1f))
        SortableTableHeader("Проєкт", sort == ReportSort.Project, ascending, { onSort(ReportSort.Project) }, Modifier.width(190.dp))
        SortableTableHeader("Субпроєкт", sort == ReportSort.Subproject, ascending, { onSort(ReportSort.Subproject) }, Modifier.width(190.dp))
        SortableTableHeader(LocalizationManager.t("status"), sort == ReportSort.Status, ascending, { onSort(ReportSort.Status) }, Modifier.width(130.dp))
        SortableTableHeader(LocalizationManager.t("uploaded_by"), sort == ReportSort.Author, ascending, { onSort(ReportSort.Author) }, Modifier.width(100.dp))
        Text(LocalizationManager.t("actions"), Modifier.width(192.dp), style = MaterialTheme.typography.labelLarge)
    }
}
