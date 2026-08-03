package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.ApiFinancialRecord
import oms.data.ApiProjectDocument
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager

private data class ProjectActRow(
    val projectUuid: String,
    val projectName: String,
    val act: ApiFinancialRecord
)

private data class ActDocumentRow(
    val projectUuid: String,
    val projectName: String,
    val document: ApiProjectDocument
)
private enum class FinancialSort { Number, Project, ActDate, PaymentDate, Amount, Currency, Milestone, Author }

@Composable
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    val uriHandler = LocalUriHandler.current
    var acts by remember { mutableStateOf<List<ProjectActRow>>(emptyList()) }
    var actDocuments by remember { mutableStateOf<List<ActDocumentRow>>(emptyList()) }
    var sort by remember { mutableStateOf(FinancialSort.ActDate) }
    var ascending by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var editAct by remember { mutableStateOf<ProjectActRow?>(null) }
    var addAct by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        ProjectRepository.refresh()
        acts = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.financials(project.id) }.getOrNull()?.data.orEmpty()
                .filter { it.recordType == "act" }
                .map { ProjectActRow(project.id, project.name, it) }
        }.sortedByDescending { it.act.recordDate }
        actDocuments = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.projectDocuments(project.id) }.getOrDefault(emptyList())
                .filter { it.docType == "act" }
                .map { ActDocumentRow(project.id, project.name, it) }
        }
    }

    val completedWorksTotal = acts.sumOf { it.act.amount }
    val visibleActs = acts.sortedWith(compareBy<ProjectActRow> {
        when (sort) {
            FinancialSort.Number -> it.act.referenceNumber
            FinancialSort.Project -> it.projectName
            FinancialSort.ActDate -> it.act.recordDate
            FinancialSort.PaymentDate -> it.act.paymentDate.orEmpty()
            FinancialSort.Amount -> it.act.amount.toString().padStart(20, '0')
            FinancialSort.Currency -> it.act.currency
            FinancialSort.Milestone -> it.act.milestone.orEmpty()
            FinancialSort.Author -> "admin"
        }
    }.let { if (ascending) it else it.reversed() })
    fun selectSort(column: FinancialSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(LocalizationManager.t("financial_monitoring"), style = MaterialTheme.typography.headlineMedium)
        Text("Only completed works confirmed by acts are included.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sum of completed works by acts", style = MaterialTheme.typography.titleMedium)
                Text(completedWorksTotal.toMoney(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Acts", style = MaterialTheme.typography.titleLarge)
            if (canManageFinancials) Button(onClick = { addAct = true }) { Text("Add act") }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinancialTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visibleActs.isEmpty()) Text("No acts found.")
                visibleActs.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.act.referenceNumber, Modifier.width(130.dp))
                        Text(row.projectName, Modifier.weight(1.25f))
                        Text(row.act.recordDate, Modifier.width(105.dp))
                        Text(row.act.paymentDate ?: "—", Modifier.width(105.dp))
                        Text(row.act.amount.toMoney(), Modifier.width(130.dp))
                        Text(row.act.currency, Modifier.width(65.dp))
                        Text(row.act.milestone ?: "—", Modifier.weight(1f))
                        Text("admin", Modifier.width(75.dp))
                        if (canManageFinancials) {
                            TableActionIconButton("Edit act", Icons.Default.Edit) { editAct = row }
                            TableActionIconButton("Delete act", Icons.Default.Delete) {
                                scope.launch {
                                    if (OmsApiClient.deleteFinancialRecord(row.projectUuid, row.act.uuid)) reloadKey++
                                    else errorMessage = "Could not delete act."
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        Text("Act documents", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (actDocuments.isEmpty()) Text("No act documents found.")
                actDocuments.forEach { row ->
                    Text(row.document.fileName, style = MaterialTheme.typography.titleMedium)
                    Text(row.projectName)
                    Button(onClick = { uriHandler.openUri("http://localhost:8080/api/v1/projects/${row.projectUuid}/documents/${row.document.uuid}/download") }) {
                        Text("Open document")
                    }
                    HorizontalDivider()
                }
            }
        }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (addAct || editAct != null) {
            ActEditorDialog(editAct, ProjectRepository.projects, { addAct = false; editAct = null }) { projectUuid, request ->
                scope.launch {
                    runCatching {
                        if (editAct == null) OmsApiClient.createFinancialRecord(projectUuid, request)
                        else OmsApiClient.updateFinancialRecord(projectUuid, editAct!!.act.uuid, request)
                    }.onSuccess { addAct = false; editAct = null; reloadKey++ }
                        .onFailure { errorMessage = "Could not save act." }
                }
            }
        }
    }
}

@Composable
private fun ActEditorDialog(existing: ProjectActRow?, projects: List<oms.model.Project>, onDismiss: () -> Unit, onSave: (String, oms.data.FinancialRecordRequest) -> Unit) {
    var projectUuid by remember { mutableStateOf(existing?.projectUuid ?: projects.firstOrNull()?.id) }
    var reference by remember { mutableStateOf(existing?.act?.referenceNumber ?: "") }
    var amount by remember { mutableStateOf(existing?.act?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(existing?.act?.recordDate ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.id == projectUuid }
    val valid = projectUuid != null && reference.isNotBlank() && amount.toLongOrNull()?.let { it > 0 } == true && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (existing == null) "Add act" else "Edit act", style = MaterialTheme.typography.titleLarge)
            Box { OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected?.name ?: "Select project") }
                DropdownMenu(expanded, { expanded = false }) { projects.forEach { p -> DropdownMenuItem({ Text(p.name) }, { projectUuid = p.id; expanded = false }) } } }
            OutlinedTextField(reference, { reference = it }, label = { Text("Act number") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount, UAH") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = { onSave(projectUuid!!, oms.data.FinancialRecordRequest("act", reference, amount.toLong(), "UAH", date)) }, enabled = valid) { Text("Save") }
            }
        }
    }
}

@Composable
private fun FinancialTableHeader(sort: FinancialSort, ascending: Boolean, onSort: (FinancialSort) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        SortableTableHeader("Act no.", sort == FinancialSort.Number, ascending, { onSort(FinancialSort.Number) }, Modifier.width(130.dp))
        SortableTableHeader("Project", sort == FinancialSort.Project, ascending, { onSort(FinancialSort.Project) }, Modifier.weight(1.25f))
        SortableTableHeader("Act date", sort == FinancialSort.ActDate, ascending, { onSort(FinancialSort.ActDate) }, Modifier.width(105.dp))
        SortableTableHeader("Payment date", sort == FinancialSort.PaymentDate, ascending, { onSort(FinancialSort.PaymentDate) }, Modifier.width(105.dp))
        SortableTableHeader("Amount", sort == FinancialSort.Amount, ascending, { onSort(FinancialSort.Amount) }, Modifier.width(130.dp))
        SortableTableHeader("Curr.", sort == FinancialSort.Currency, ascending, { onSort(FinancialSort.Currency) }, Modifier.width(65.dp))
        SortableTableHeader("Milestone", sort == FinancialSort.Milestone, ascending, { onSort(FinancialSort.Milestone) }, Modifier.weight(1f))
        SortableTableHeader("By", sort == FinancialSort.Author, ascending, { onSort(FinancialSort.Author) }, Modifier.width(75.dp))
        Spacer(Modifier.width(96.dp))
    }
}

@Composable
private fun FinancialAccessDenied() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Card(Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(LocalizationManager.t("access_restricted"), style = MaterialTheme.typography.headlineSmall)
                Text(LocalizationManager.t("financial_access_restricted"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun Long.toMoney(): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} UAH"
