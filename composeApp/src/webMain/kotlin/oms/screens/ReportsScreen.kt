package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
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
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("openInspectionPhotoUpload")
external fun openInspectionPhotoUpload(reportUuid: String)

private data class ReportRow(val projectUuid: String, val projectName: String, val report: ApiInspectionReport)
private enum class ReportSort { Date, Report, Status, Author }

@Composable
fun ReportsScreen(onNewInspection: () -> Unit = {}) {
    var reports by remember { mutableStateOf<List<ReportRow>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportToMove by remember { mutableStateOf<ReportRow?>(null) }
    var findingsReport by remember { mutableStateOf<ReportRow?>(null) }
    val scope = rememberCoroutineScope()
    var sort by remember { mutableStateOf(ReportSort.Date) }
    var ascending by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        reports = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectReports(project.id) }.getOrDefault(emptyList())
                .map { ReportRow(project.id, project.name, it) }
        }.sortedByDescending { it.report.inspectionDate }
    }
    val visible = reports.filter { status == null || it.report.status == status }.sortedWith(
        compareBy<ReportRow> {
            when (sort) {
                ReportSort.Date -> it.report.inspectionDate
                ReportSort.Report -> "${it.report.summary.orEmpty()} ${it.projectName}"
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
                FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value ?: LocalizationManager.t("all")) })
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visible.isEmpty()) Text("No inspection reports found.")
                visible.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.report.inspectionDate, Modifier.width(105.dp))
                        Column(Modifier.weight(1.35f)) {
                            Text(row.report.summary ?: "Inspection report", style = MaterialTheme.typography.bodyMedium)
                            Text(row.projectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.width(130.dp)) { ReportStatusChip(row.report.status) }
                        Text("admin", Modifier.width(100.dp))
                        TableActionIconButton("Upload photo", Icons.Default.PhotoCamera) { openInspectionPhotoUpload(row.report.uuid) }
                        TableActionIconButton("Findings", Icons.AutoMirrored.Filled.FactCheck) { findingsReport = row }
                        TableActionIconButton("Move report", Icons.Default.SwapHoriz) { reportToMove = row }
                        TableActionIconButton("Delete report", Icons.Default.Delete) {
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
                                if (it.report.uuid == report.report.uuid) it.copy(projectUuid = target.id, projectName = target.name) else it
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
    } else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inspection findings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${report.report.summary ?: "Inspection report"} — ${report.projectName}", style = MaterialTheme.typography.bodySmall)
                if (findings.isEmpty()) Text("No findings yet.")
                findings.forEach { finding ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${finding.category} • ${finding.severity.uppercase()}${if (finding.isResolved) " • RESOLVED" else ""}", style = MaterialTheme.typography.titleSmall)
                            Text(finding.description)
                            finding.recommendation?.let { Text("Recommendation: $it", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editing = finding }) { Text("Edit") }
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching { OmsApiClient.updateInspectionFinding(report.report.uuid, finding.uuid, UpdateInspectionFindingRequest(finding.category, finding.severity, finding.description, finding.recommendation, !finding.isResolved)) }
                                            .onSuccess { refresh() }.onFailure { error = "Could not update finding." }
                                    }
                                }) { Text(if (finding.isResolved) "Reopen" else "Resolve") }
                                TextButton(onClick = {
                                    scope.launch {
                                        if (OmsApiClient.deleteInspectionFinding(report.report.uuid, finding.uuid)) refresh()
                                        else error = "Could not delete finding."
                                    }
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = { adding = true }) { Text("Add finding") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (finding == null) "Add finding" else "Edit finding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("low", "medium", "high", "critical").forEach { value -> FilterChip(selected = severity == value, onClick = { severity = value }, label = { Text(value) }) }
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(recommendation, { recommendation = it }, label = { Text("Recommendation") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (finding != null) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(isResolved, { isResolved = it }); Text("Resolved") }
            }
        },
        confirmButton = { Button(onClick = { onSave(category, severity, description, recommendation.ifBlank { null }, isResolved) }, enabled = category.isNotBlank() && description.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun MoveReportDialog(report: ReportRow, onDismiss: () -> Unit, onMove: (oms.model.Project) -> Unit) {
    var selectedUuid by remember(report.report.uuid) { mutableStateOf<String?>(null) }
    val projects = ProjectRepository.projects.filter { it.id != report.projectUuid }
    val selected = projects.firstOrNull { it.id == selectedUuid }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move inspection report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${report.report.summary ?: "Inspection report"} is currently linked to ${report.projectName}.")
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selected?.name ?: "Select target project")
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
                if (projects.isEmpty()) Text("There is no other project to select.")
            }
        },
        confirmButton = { Button(onClick = { selected?.let(onMove) }, enabled = selected != null) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReportTableHeader(sort: ReportSort, ascending: Boolean, onSort: (ReportSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SortableTableHeader("Date", sort == ReportSort.Date, ascending, { onSort(ReportSort.Date) }, Modifier.width(105.dp))
        SortableTableHeader("Report / project", sort == ReportSort.Report, ascending, { onSort(ReportSort.Report) }, Modifier.weight(1.35f))
        SortableTableHeader("Status", sort == ReportSort.Status, ascending, { onSort(ReportSort.Status) }, Modifier.width(130.dp))
        SortableTableHeader("Uploaded by", sort == ReportSort.Author, ascending, { onSort(ReportSort.Author) }, Modifier.width(100.dp))
        Text("Actions", Modifier.width(192.dp), style = MaterialTheme.typography.labelLarge)
    }
}
