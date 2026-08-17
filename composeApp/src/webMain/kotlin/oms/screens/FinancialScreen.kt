package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.data.ApiFinancialRecord
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager
import oms.components.OmsDateField
import oms.components.toOmsDate
import oms.components.InlineOptionPicker
import oms.components.currentIsoDate
import kotlin.js.JsName

@JsName("openFinancialImport")
external fun openFinancialImport(projectUuid: String)

@JsName("downloadFinancialExport")
external fun downloadFinancialExport(projectUuid: String)

private data class ProjectActRow(
    val projectUuid: String,
    val projectName: String,
    val act: ApiFinancialRecord
)

private enum class FinancialSort { Number, Type, Project, ActDate, PaymentDate, Amount, Currency, Milestone, Author }

@Composable
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    var acts by remember { mutableStateOf<List<ProjectActRow>>(emptyList()) }
    var recordTypeFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(FinancialSort.ActDate) }
    var ascending by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var editAct by remember { mutableStateOf<ProjectActRow?>(null) }
    var addAct by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        ProjectRepository.refresh()
        acts = ProjectRepository.projects.flatMap { project ->
            runCatching { OmsApiClient.financials(project.id) }.getOrNull()?.data.orEmpty()
                .map { ProjectActRow(project.id, project.name, it) }
        }.sortedByDescending { it.act.recordDate }
    }

    val completedWorksTotal = acts.filter { it.act.recordType == "act" }.sumOf { it.act.amount }
    val visibleActs = acts.filter { recordTypeFilter == null || it.act.recordType == recordTypeFilter }.sortedWith(compareBy<ProjectActRow> {
        when (sort) {
            FinancialSort.Number -> it.act.referenceNumber
            FinancialSort.Type -> it.act.recordType
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(LocalizationManager.t("financial_monitoring"), style = MaterialTheme.typography.headlineMedium)
        Text(LocalizationManager.t("only_completed_works"), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(LocalizationManager.t("completed_works_by_acts"), style = MaterialTheme.typography.titleMedium)
                Text(completedWorksTotal.toMoney(), style = MaterialTheme.typography.headlineMedium)
            }
        }

        MonthlyPaymentsChart(acts.map { it.act })

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(LocalizationManager.t("financial_records"), style = MaterialTheme.typography.titleLarge)
            if (canManageFinancials) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showTransferDialog = true }) { Text(LocalizationManager.t("import_export_xlsx")) }
                    Button(onClick = { addAct = true }) { Text(LocalizationManager.t("add_record")) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, "invoice", "act", "payment", "advance").forEach { type ->
                FilterChip(selected = recordTypeFilter == type, onClick = { recordTypeFilter = type }, label = { Text(type?.let { LocalizationManager.t("record_type_$it") } ?: LocalizationManager.t("all")) })
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FinancialTableHeader(sort, ascending, ::selectSort)
                HorizontalDivider()
                if (visibleActs.isEmpty()) Text(LocalizationManager.t("no_acts"))
                visibleActs.forEach { row ->
                    Row(Modifier.width(1_500.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.act.referenceNumber, Modifier.width(130.dp))
                        Text(row.act.recordType.replaceFirstChar { it.uppercase() }, Modifier.width(95.dp))
                        Text(row.projectName, Modifier.weight(1.25f))
                        Text(row.act.recordDate.toOmsDate(), Modifier.width(105.dp))
                        Text(row.act.paymentDate.toOmsDate(), Modifier.width(105.dp))
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
                        } else Spacer(Modifier.width(96.dp))
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
        if (showTransferDialog) {
            FinancialTransferDialog(
                projects = ProjectRepository.projects,
                onDismiss = { showTransferDialog = false },
                onImport = { projectUuid -> openFinancialImport(projectUuid); showTransferDialog = false },
                onExport = { projectUuid -> downloadFinancialExport(projectUuid); showTransferDialog = false }
            )
        }
    }
}

@Composable
private fun FinancialTransferDialog(
    projects: List<oms.model.Project>,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onExport: (String) -> Unit
) {
    var projectUuid by remember { mutableStateOf(projects.firstOrNull()?.id) }
    val selected = projects.firstOrNull { it.id == projectUuid }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalizationManager.t("financial_transfer_title")) },
        text = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InlineOptionPicker(options = projects, selected = selected, prompt = LocalizationManager.t("select_project"), onSelect = { projectUuid = it.id }, itemLabel = { it.name })
            Text(LocalizationManager.t("financial_import_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) } },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { projectUuid?.let(onExport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("export_to_excel")) }
                Button(onClick = { projectUuid?.let(onImport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("import_xls")) }
            }
        }
    )
}

@Composable
private fun ActEditorDialog(existing: ProjectActRow?, projects: List<oms.model.Project>, onDismiss: () -> Unit, onSave: (String, oms.data.FinancialRecordRequest) -> Unit) {
    var projectUuid by remember { mutableStateOf(existing?.projectUuid ?: projects.firstOrNull()?.id) }
    var reference by remember { mutableStateOf(existing?.act?.referenceNumber ?: "") }
    var recordType by remember { mutableStateOf(existing?.act?.recordType ?: "act") }
    var amount by remember { mutableStateOf(existing?.act?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(existing?.act?.recordDate ?: currentIsoDate()) }
    val selected = projects.firstOrNull { it.id == projectUuid }
    val valid = projectUuid != null && reference.isNotBlank() && amount.toLongOrNull()?.let { it > 0 } == true && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (existing == null) LocalizationManager.t("add_financial_record") else LocalizationManager.t("edit_financial_record"), style = MaterialTheme.typography.titleLarge)
            InlineOptionPicker(options = projects, selected = selected, prompt = LocalizationManager.t("select_project"), onSelect = { projectUuid = it.id }, itemLabel = { it.name })
            OutlinedTextField(reference, { reference = it }, label = { Text(LocalizationManager.t("reference_number")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("invoice", "act", "payment", "advance").forEach { type -> FilterChip(selected = recordType == type, onClick = { recordType = type }, label = { Text(type) }) }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { entered ->
                    val filtered = entered
                        .filter { it.isDigit() || it == '.' || it == ',' }
                        .replace(',', '.')
                    amount = if (filtered.count { it == '.' } <= 1) filtered else amount
                },
                label = { Text(LocalizationManager.t("amount_uah")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OmsDateField(date, { date = it }, LocalizationManager.t("date"), Modifier.fillMaxWidth(), true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onSave(projectUuid!!, oms.data.FinancialRecordRequest(recordType, reference, amount.toLong(), "UAH", date)) }, enabled = valid) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

@Composable
private fun FinancialTableHeader(sort: FinancialSort, ascending: Boolean, onSort: (FinancialSort) -> Unit) {
    Row(Modifier.width(1_500.dp).padding(vertical = 6.dp)) {
        SortableTableHeader("Номер", sort == FinancialSort.Number, ascending, { onSort(FinancialSort.Number) }, Modifier.width(130.dp))
        SortableTableHeader("Тип", sort == FinancialSort.Type, ascending, { onSort(FinancialSort.Type) }, Modifier.width(95.dp))
        SortableTableHeader("Проєкт", sort == FinancialSort.Project, ascending, { onSort(FinancialSort.Project) }, Modifier.weight(1.25f))
        SortableTableHeader("Дата акта", sort == FinancialSort.ActDate, ascending, { onSort(FinancialSort.ActDate) }, Modifier.width(105.dp))
        SortableTableHeader("Дата оплати", sort == FinancialSort.PaymentDate, ascending, { onSort(FinancialSort.PaymentDate) }, Modifier.width(105.dp))
        SortableTableHeader("Сума", sort == FinancialSort.Amount, ascending, { onSort(FinancialSort.Amount) }, Modifier.width(130.dp))
        SortableTableHeader("Вал.", sort == FinancialSort.Currency, ascending, { onSort(FinancialSort.Currency) }, Modifier.width(65.dp))
        SortableTableHeader("Етап", sort == FinancialSort.Milestone, ascending, { onSort(FinancialSort.Milestone) }, Modifier.weight(1f))
        SortableTableHeader("Автор", sort == FinancialSort.Author, ascending, { onSort(FinancialSort.Author) }, Modifier.width(75.dp))
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
