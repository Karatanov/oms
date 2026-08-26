package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import oms.components.WasmSafeOverlay
import kotlin.js.JsName

@JsName("openFinancialImport")
external fun openFinancialImport(projectUuid: String, onComplete: (String) -> Unit)

@JsName("downloadFinancialExport")
external fun downloadFinancialExport(projectUuid: String)

private data class ProjectActRow(
    val projectUuid: String,
    val subprojectName: String,
    val subprojectPartCode: String?,
    val act: ApiFinancialRecord
)

private enum class FinancialSort { Number, Type, Purpose, Subproject, SubprojectPartCode, ActDate, PaymentDate, Amount, Currency, Description, Author }

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
    val pageScrollState = rememberScrollState()

    LaunchedEffect(reloadKey) {
        val records = coroutineScope {
            val refreshProjects = async { ProjectRepository.refresh() }
            val loadRecords = async { OmsApiClient.allFinancialRecords() }
            refreshProjects.await()
            loadRecords.await()
        }
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        acts = records.mapNotNull { item ->
            projectsById[item.projectUuid]?.let { project ->
                val ancestry = generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList().asReversed()
                val subproject = ancestry.firstOrNull { it.projectType == "subproject" }
                val partCode = ancestry.firstOrNull { it.projectType == "subproject_part" }?.siteNumber
                ProjectActRow(project.id, subproject?.name ?: project.name, partCode, item.record)
            }
        }.sortedByDescending { it.act.recordDate }
    }

    // The editor is intentionally placed after the table.  Reveal it as soon as it
    // is opened so that clicking an action always produces visible feedback.
    LaunchedEffect(addAct, editAct?.act?.uuid) {
        if (addAct || editAct != null) {
            delay(50)
            pageScrollState.animateScrollTo(pageScrollState.maxValue)
        }
    }

    val completedWorksTotals = acts
        .filter { it.act.recordType == "act" }
        .groupBy { it.act.currency }
        .mapValues { (_, rows) -> rows.sumOf { it.act.amount } }
    val visibleActs = acts.filter { recordTypeFilter == null || it.act.recordType == recordTypeFilter }.sortedWith(compareBy<ProjectActRow> {
        when (sort) {
            FinancialSort.Number -> it.act.referenceNumber
            FinancialSort.Type -> it.act.recordType
            FinancialSort.Purpose -> it.act.paymentPurpose
            FinancialSort.Subproject -> it.subprojectName
            FinancialSort.SubprojectPartCode -> it.subprojectPartCode.orEmpty()
            FinancialSort.ActDate -> it.act.recordDate
            FinancialSort.PaymentDate -> it.act.paymentDate.orEmpty()
            FinancialSort.Amount -> it.act.amount.toString().padStart(20, '0')
            FinancialSort.Currency -> it.act.currency
            FinancialSort.Description -> (it.act.description ?: it.act.milestone).orEmpty()
            FinancialSort.Author -> "admin"
        }
    }.let { if (ascending) it else it.reversed() })
    fun selectSort(column: FinancialSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(pageScrollState).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(LocalizationManager.t("financial_monitoring"), style = MaterialTheme.typography.headlineMedium)
        Text(LocalizationManager.t("only_completed_works"), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(LocalizationManager.t("completed_works_by_acts"), style = MaterialTheme.typography.titleMedium)
                Text(completedWorksTotals.entries.joinToString(" • ") { (currency, amount) -> amount.toMoney(currency) }.ifBlank { "—" }, style = MaterialTheme.typography.headlineMedium)
            }
        }

        val financialChartRecords = acts.map { FinancialChartRecord(it.act, it.subprojectName) }
        MonthlyPaymentsChart(financialChartRecords)
        MonthlyEquipmentPaymentsChart(financialChartRecords)
        MonthlyTechnicalSupervisionPaymentsChart(financialChartRecords)
        MonthlyEngineerConsultantPaymentsChart(financialChartRecords)

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
                    Row(Modifier.width(1_630.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(row.act.referenceNumber, Modifier.width(130.dp))
                        Text(row.act.recordType.replaceFirstChar { it.uppercase() }, Modifier.width(95.dp))
                        Box(Modifier.width(210.dp)) { FinancialPaymentPurposeBadge(row.act.recordType, row.act.paymentPurpose) }
                        Text(row.subprojectName, Modifier.width(200.dp))
                        Text(row.subprojectPartCode ?: "—", Modifier.width(160.dp))
                        Text(row.act.recordDate.toOmsDate(), Modifier.width(105.dp))
                        Text(row.act.paymentDate.toOmsDate(), Modifier.width(105.dp))
                        Text(row.act.amount.toMoney(row.act.currency), Modifier.width(130.dp))
                        Text(row.act.currency, Modifier.width(65.dp))
                        Text(row.act.description ?: row.act.milestone ?: "—", Modifier.width(250.dp))
                        Text("admin", Modifier.width(75.dp))
                        if (canManageFinancials) {
                            TableActionIconButton(LocalizationManager.t("edit_financial_record_tooltip"), Icons.Default.Edit) { editAct = row }
                            TableActionIconButton(LocalizationManager.t("delete_financial_record_tooltip"), Icons.Default.Delete) {
                                scope.launch {
                                    if (OmsApiClient.deleteFinancialRecord(row.projectUuid, row.act.uuid)) reloadKey++
                                    else errorMessage = LocalizationManager.t("error_delete_act")
                                }
                            }
                        } else Spacer(Modifier.width(96.dp))
                    }
                    HorizontalDivider()
                }
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
        if (addAct || editAct != null) { WasmSafeOverlay {
            ActEditorDialog(editAct, ProjectRepository.projects, { addAct = false; editAct = null }) { projectUuid, request ->
                scope.launch {
                    errorMessage = null
                    runCatching {
                        if (editAct == null) OmsApiClient.createFinancialRecord(projectUuid, request)
                        else OmsApiClient.updateFinancialRecord(editAct!!.projectUuid, editAct!!.act.uuid, projectUuid, request)
                    }.onSuccess { errorMessage = null; addAct = false; editAct = null; reloadKey++ }
                        .onFailure { exception ->
                            errorMessage = LocalizationManager.t("error_save_act")
                                .replace("{message}", exception.message ?: LocalizationManager.t("unknown_error"))
                        }
                }
            }
        } }
        if (showTransferDialog) { WasmSafeOverlay {
            FinancialTransferDialog(
                projects = ProjectRepository.projects,
                onDismiss = { showTransferDialog = false },
                onImport = { projectUuid ->
                    openFinancialImport(projectUuid) { importError ->
                        if (importError.isBlank()) reloadKey++
                        else errorMessage = LocalizationManager.t("error_import_financial_records").replace("{message}", importError)
                    }
                    showTransferDialog = false
                },
                onExport = { projectUuid -> downloadFinancialExport(projectUuid); showTransferDialog = false }
            )
        }
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
    Card(Modifier.fillMaxWidth().widthIn(max = 620.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("financial_transfer_title"), style = MaterialTheme.typography.titleLarge)
            InlineOptionPicker(options = projects, selected = selected, prompt = LocalizationManager.t("select_project"), onSelect = { projectUuid = it.id }, itemLabel = { it.name })
            Text(LocalizationManager.t("financial_import_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                OutlinedButton(onClick = { projectUuid?.let(onExport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("export_to_excel")) }
                Button(onClick = { projectUuid?.let(onImport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("import_xls")) }
            }
        }
    }
}

@Composable
private fun ActEditorDialog(existing: ProjectActRow?, projects: List<oms.model.Project>, onDismiss: () -> Unit, onSave: (String, oms.data.FinancialRecordRequest) -> Unit) {
    var projectUuid by remember { mutableStateOf(existing?.projectUuid ?: projects.firstOrNull()?.id) }
    var reference by remember { mutableStateOf(existing?.act?.referenceNumber ?: "") }
    var recordType by remember { mutableStateOf(existing?.act?.recordType ?: "act") }
    var amount by remember { mutableStateOf(existing?.act?.amount?.toString() ?: "") }
    var currency by remember { mutableStateOf(existing?.act?.currency ?: "EUR") }
    var date by remember { mutableStateOf(existing?.act?.recordDate ?: currentIsoDate()) }
    var description by remember { mutableStateOf(existing?.act?.description ?: existing?.act?.milestone ?: "") }
    var paymentPurpose by remember { mutableStateOf(existing?.act?.paymentPurpose ?: "works") }
    val selected = projects.firstOrNull { it.id == projectUuid }
    val valid = projectUuid != null && reference.isNotBlank() && amount.toLongOrNull()?.let { it > 0 } == true && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    Card(Modifier.fillMaxWidth().widthIn(max = 720.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (existing == null) LocalizationManager.t("add_financial_record") else LocalizationManager.t("edit_financial_record"), style = MaterialTheme.typography.titleLarge)
            InlineOptionPicker(options = projects, selected = selected, prompt = LocalizationManager.t("select_project"), onSelect = { projectUuid = it.id }, itemLabel = { it.name })
            OutlinedTextField(reference, { reference = it }, label = { Text(LocalizationManager.t("reference_number")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("invoice", "act", "payment", "advance").forEach { type -> FilterChip(selected = recordType == type, onClick = { recordType = type }, label = { Text(LocalizationManager.t("record_type_$type")) }) }
            }
            if (recordType in setOf("payment", "advance")) {
                Text(LocalizationManager.t("payment_purpose"), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("works", "equipment", "technical_supervision", "engineer_consultant").forEach { purpose ->
                        FilterChip(
                            selected = paymentPurpose == purpose,
                            onClick = { paymentPurpose = purpose },
                            label = { Text(LocalizationManager.t("payment_purpose_$purpose")) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { entered ->
                    val filtered = entered
                        .filter { it.isDigit() || it == '.' || it == ',' }
                        .replace(',', '.')
                    amount = if (filtered.count { it == '.' } <= 1) filtered else amount
                },
                label = { Text("${LocalizationManager.t("amount")}, $currency") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(LocalizationManager.t("currency"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EUR", "UAH").forEach { code ->
                    FilterChip(
                        selected = currency == code,
                        onClick = { currency = code },
                        label = { Text(LocalizationManager.t("currency_${code.lowercase()}")) }
                    )
                }
            }
            OmsDateField(date, { date = it }, LocalizationManager.t("date"), Modifier.fillMaxWidth(), true)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(LocalizationManager.t(if (recordType == "payment") "payment_basis" else "description")) },
                placeholder = if (recordType == "payment") { { Text(LocalizationManager.t("payment_basis_hint")) } } else null,
                modifier = Modifier.fillMaxWidth(),
                minLines = if (recordType == "payment") 2 else 1
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onSave(projectUuid!!, oms.data.FinancialRecordRequest(recordType, reference, amount.toLong(), currency, date, description = description.ifBlank { null }, milestone = null, paymentPurpose = paymentPurpose)) }, enabled = valid) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

@Composable
private fun FinancialTableHeader(sort: FinancialSort, ascending: Boolean, onSort: (FinancialSort) -> Unit) {
    Row(Modifier.width(1_630.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("reference_number"), sort == FinancialSort.Number, ascending, { onSort(FinancialSort.Number) }, Modifier.width(130.dp))
        SortableTableHeader(LocalizationManager.t("type"), sort == FinancialSort.Type, ascending, { onSort(FinancialSort.Type) }, Modifier.width(95.dp))
        SortableTableHeader(LocalizationManager.t("payment_purpose"), sort == FinancialSort.Purpose, ascending, { onSort(FinancialSort.Purpose) }, Modifier.width(210.dp))
        SortableTableHeader(LocalizationManager.t("subproject"), sort == FinancialSort.Subproject, ascending, { onSort(FinancialSort.Subproject) }, Modifier.width(200.dp))
        SortableTableHeader(LocalizationManager.t("subproject_part_code_label"), sort == FinancialSort.SubprojectPartCode, ascending, { onSort(FinancialSort.SubprojectPartCode) }, Modifier.width(160.dp))
        SortableTableHeader(LocalizationManager.t("act_date"), sort == FinancialSort.ActDate, ascending, { onSort(FinancialSort.ActDate) }, Modifier.width(105.dp))
        SortableTableHeader(LocalizationManager.t("payment_date"), sort == FinancialSort.PaymentDate, ascending, { onSort(FinancialSort.PaymentDate) }, Modifier.width(105.dp))
        SortableTableHeader(LocalizationManager.t("amount"), sort == FinancialSort.Amount, ascending, { onSort(FinancialSort.Amount) }, Modifier.width(130.dp))
        SortableTableHeader(LocalizationManager.t("currency_short"), sort == FinancialSort.Currency, ascending, { onSort(FinancialSort.Currency) }, Modifier.width(65.dp))
        SortableTableHeader(LocalizationManager.t("description"), sort == FinancialSort.Description, ascending, { onSort(FinancialSort.Description) }, Modifier.width(250.dp))
        SortableTableHeader(LocalizationManager.t("author"), sort == FinancialSort.Author, ascending, { onSort(FinancialSort.Author) }, Modifier.width(75.dp))
        Spacer(Modifier.width(96.dp))
    }
}

@Composable
private fun FinancialPaymentPurposeBadge(recordType: String, purpose: String) {
    if (recordType !in setOf("payment", "advance")) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (labelKey, color) = when (purpose) {
        "equipment" -> "payment_purpose_equipment" to Color(0xFFC68642)
        "technical_supervision" -> "payment_purpose_technical_supervision" to Color(0xFF4D9F76)
        "engineer_consultant" -> "payment_purpose_engineer_consultant" to Color(0xFF7666A5)
        else -> "payment_purpose_works" to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Text(
            LocalizationManager.t(labelKey),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
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

private fun Long.toMoney(currency: String): String = "${toString().reversed().chunked(3).joinToString(" ").reversed()} $currency"
