package oms.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.data.ApiProcurementRecord
import oms.data.ApiDashboard
import oms.data.OmsApiClient
import oms.data.ProcurementRecordRequest
import oms.components.OmsDateField
import oms.components.TableActionIconButton
import oms.components.InlineOptionPicker
import oms.components.UkraineRegionAutocomplete
import oms.components.currentIsoDate
import oms.components.WasmSafeOverlay
import oms.localization.LocalizationManager
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProcurementScreen(canManageProcurements: Boolean) {
    var records by remember { mutableStateOf<List<ApiProcurementRecord>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editorRecord by remember { mutableStateOf<ApiProcurementRecord?>(null) }
    var creating by remember { mutableStateOf(false) }
    var recordPendingDeletion by remember { mutableStateOf<ApiProcurementRecord?>(null) }
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    val scope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.procurements() }
            .onSuccess { records = it.sortedWith(compareBy({ record -> record.batchId }, { record -> record.recordNumber })) }
            .onFailure { error = LocalizationManager.t("procurement_load_error") }
        dashboard = runCatching { OmsApiClient.dashboard() }.getOrNull()
    }
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
            .padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(LocalizationManager.t("procurement_title"), style = MaterialTheme.typography.headlineMedium)
            if (canManageProcurements) Button(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("add_procurement_record")) }
        }
        Spacer(Modifier.height(8.dp))
        Text(LocalizationManager.t("procurement_subtitle"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { MetricsChart("procurement_status_chart", "procurement_status_chart_hint", dashboard?.procurementStatusCounts.orEmpty()) }
            Box(Modifier.weight(1f)) {
                MetricsChart(
                    "signed_construction_contracts",
                    "signed_construction_contracts_hint",
                    dashboard?.monthlySignedConstructionContracts.orEmpty(),
                    centerYearLabels = true
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            records == null -> CircularProgressIndicator()
            else -> ProcurementTable(records!!, canManageProcurements, { editorRecord = it }, { recordPendingDeletion = it })
        }
    }
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
        if (creating) WasmSafeOverlay {
            ProcurementEditorDialog(null, onDismiss = { creating = false }) { request ->
            scope.launch { runCatching { OmsApiClient.createProcurement(request) }
                .onSuccess { records = (records.orEmpty() + it).sortedBy { record -> record.recordNumber }; creating = false }
                .onFailure { error = LocalizationManager.t("error_create_procurement").replace("{message}", it.message.orEmpty()) } }
            }
        }
        editorRecord?.let { existing -> WasmSafeOverlay {
            ProcurementEditorDialog(existing, onDismiss = { editorRecord = null }) { request ->
            scope.launch { runCatching { OmsApiClient.updateProcurement(existing.id, request) }
                .onSuccess { saved -> records = records.orEmpty().map { if (it.id == saved.id) saved else it }; editorRecord = null }
                .onFailure { error = LocalizationManager.t("error_update_procurement").replace("{message}", it.message.orEmpty()) } }
            }
        }
        }
        recordPendingDeletion?.let { record -> WasmSafeOverlay {
            Card(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(LocalizationManager.t("delete_procurement_title"), style = MaterialTheme.typography.titleLarge)
                    Text(LocalizationManager.t("delete_procurement_confirmation").replace("{number}", record.recordNumber.toString()))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        OutlinedButton(onClick = { recordPendingDeletion = null }) { Text(LocalizationManager.t("cancel")) }
                        Button(onClick = { scope.launch { if (OmsApiClient.deleteProcurement(record.id)) { records = records.orEmpty().filterNot { it.id == record.id }; recordPendingDeletion = null } else error = LocalizationManager.t("error_delete_procurement") } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(LocalizationManager.t("delete")) }
                    }
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProcurementTable(
    records: List<ApiProcurementRecord>,
    canManage: Boolean,
    onEdit: (ApiProcurementRecord) -> Unit,
    onDelete: (ApiProcurementRecord) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    fun scrollHorizontally(delta: Float) = scope.launch { horizontalScrollState.animateScrollBy(delta) }
    Box(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().horizontalScroll(horizontalScrollState)) {
        ProcurementRow(procurementHeaderLabels(), showActions = canManage, isHeader = true)
        HorizontalDivider()
        records.forEach { record ->
            ProcurementRow(listOf(
                record.recordNumber.toString(), record.batchId.toString(), record.oblastName, record.oblastId,
                record.subProjectId, record.subProjectLotId, LocalizationManager.procurementStatus(record.purchaseStatus), record.tenderId.orEmpty(),
                record.prozorroTenderId.orEmpty(), record.contractorNameUkr.orEmpty(), record.contractorNameEng.orEmpty(),
                record.contractorId.orEmpty(), record.contractDate.orEmpty(), record.contractEndDate.orEmpty(),
                record.contractDurationMonths?.toString().orEmpty(), record.contractAmountUah.format(0),
                record.contractAmountEur.format(2), record.financingContractDifferencePct?.let { "${(it * 100).format(2)}%" }.orEmpty()
            ), record.takeIf { canManage }, onEdit, onDelete, showActions = canManage)
            HorizontalDivider()
        }
    }
    TooltipBox(
        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(LocalizationManager.t("scroll_table_left")) } },
        state = rememberTooltipState()
    ) {
        FilledIconButton(onClick = { scrollHorizontally(-620f) }) {
            Icon(Icons.Default.KeyboardArrowLeft, LocalizationManager.t("scroll_table_left"))
        }
    }
    TooltipBox(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(LocalizationManager.t("scroll_table_right")) } },
        state = rememberTooltipState()
    ) {
        FilledIconButton(onClick = { scrollHorizontally(620f) }) {
            Icon(Icons.Default.KeyboardArrowRight, LocalizationManager.t("scroll_table_right"))
        }
    }
    }
}

@Composable
private fun ProcurementRow(
    values: List<String>,
    record: ApiProcurementRecord? = null,
    onEdit: (ApiProcurementRecord) -> Unit = {},
    onDelete: (ApiProcurementRecord) -> Unit = {},
    showActions: Boolean = false,
    isHeader: Boolean = false
) {
    Row(
        Modifier.widthIn(min = 3_300.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showActions) {
            if (isHeader) {
                Text(LocalizationManager.t("actions"), Modifier.width(96.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            } else if (record != null) {
                Box(Modifier.offset(y = (-4).dp)) {
                TableActionIconButton(LocalizationManager.t("edit_procurement_record"), Icons.Default.Edit) { onEdit(record) }
                }
                Box(Modifier.offset(y = (-4).dp)) {
                TableActionIconButton(LocalizationManager.t("delete_procurement_record"), Icons.Default.Delete) { onDelete(record) }
                }
            }
        }
        values.take(columnWidths.size).zip(columnWidths).forEach { (value, width) ->
            Text(value, Modifier.width(width.dp).padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private fun procurementHeaderLabels() = listOf(
    "proc_number", "proc_batch_number", "proc_oblast_name", "proc_oblast_id", "proc_subproject_id",
    "proc_subproject_lot_id", "procurement_status", "proc_tender_id", "proc_prozorro_tender_id",
    "proc_contractor_name_uk", "proc_contractor_name_en", "proc_contractor_edrpou", "proc_contract_date",
    "proc_contract_end_date", "proc_contract_duration", "proc_contract_amount_uah", "proc_contract_amount_eur",
    "proc_financing_difference"
).map(LocalizationManager::t)
private val columnWidths = listOf(55, 85, 180, 100, 145, 160, 220, 185, 250, 260, 260, 160, 120, 160, 160, 190, 180, 180)
private val procurementStatuses = listOf(
    "Не розпочато / Not Started",
    "Закупівля триває / Tender Ongoing",
    "Повідомлення про намір укласти договір / Contract award notice",
    "Договір укладено / Contract signed",
    "Відмінено / Cancelled",
    "Договір розірвано / Contract terminated"
)
private fun Double?.format(decimals: Int): String = this?.let { value ->
    val multiplier = if (decimals == 0) 1.0 else 100.0
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    if (decimals == 0) rounded.toInt().toString() else rounded.toString()
}.orEmpty()

@Composable
private fun ProcurementEditorDialog(
    existing: ApiProcurementRecord?,
    onDismiss: () -> Unit,
    onSave: (ProcurementRecordRequest) -> Unit
) {
    var number by remember(existing?.id) { mutableStateOf(existing?.recordNumber?.toString().orEmpty()) }
    var batch by remember(existing?.id) { mutableStateOf(existing?.batchId?.toString().orEmpty()) }
    var oblastName by remember(existing?.id) { mutableStateOf(existing?.oblastName.orEmpty()) }
    var oblastId by remember(existing?.id) { mutableStateOf(existing?.oblastId.orEmpty()) }
    var subprojectId by remember(existing?.id) { mutableStateOf(existing?.subProjectId.orEmpty()) }
    var lotId by remember(existing?.id) { mutableStateOf(existing?.subProjectLotId.orEmpty()) }
    var status by remember(existing?.id) { mutableStateOf(existing?.purchaseStatus ?: procurementStatuses.first()) }
    var tenderId by remember(existing?.id) { mutableStateOf(existing?.tenderId.orEmpty()) }
    var prozorroId by remember(existing?.id) { mutableStateOf(existing?.prozorroTenderId.orEmpty()) }
    var contractorUkr by remember(existing?.id) { mutableStateOf(existing?.contractorNameUkr.orEmpty()) }
    var contractorEng by remember(existing?.id) { mutableStateOf(existing?.contractorNameEng.orEmpty()) }
    var contractorId by remember(existing?.id) { mutableStateOf(existing?.contractorId.orEmpty()) }
    var contractDate by remember(existing?.id) { mutableStateOf(existing?.contractDate ?: currentIsoDate()) }
    var contractEndDate by remember(existing?.id) { mutableStateOf(existing?.contractEndDate ?: currentIsoDate()) }
    var duration by remember(existing?.id) { mutableStateOf(existing?.contractDurationMonths?.toString().orEmpty()) }
    var amountUah by remember(existing?.id) { mutableStateOf(existing?.contractAmountUah?.toString().orEmpty()) }
    var amountEur by remember(existing?.id) { mutableStateOf(existing?.contractAmountEur?.toString().orEmpty()) }
    var difference by remember(existing?.id) { mutableStateOf(existing?.financingContractDifferencePct?.times(100)?.toString().orEmpty()) }
    val valid = number.toIntOrNull()?.let { it > 0 } == true && batch.toIntOrNull()?.let { it > 0 } == true &&
        listOf(oblastName, oblastId, subprojectId, lotId, status).all { it.isNotBlank() }
    fun numeric(value: String, decimals: Boolean = false, update: (String) -> Unit) {
        update(value.filter { it.isDigit() || (decimals && (it == '.' || it == ',')) }.replace(',', '.').let { text -> if (decimals && text.count { it == '.' } > 1) text.dropLast(1) else text })
    }
    Card(
        Modifier.fillMaxWidth().widthIn(max = 760.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t(if (existing == null) "add_procurement_record" else "edit_procurement_record"), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(number, { numeric(it, update = { value -> number = value }) }, label = { Text("№ *") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(batch, { numeric(it, update = { value -> batch = value }) }, label = { Text("${LocalizationManager.t("proc_batch_number")} *") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                UkraineRegionAutocomplete(oblastName, { oblastName = it }, LocalizationManager.t("proc_oblast_name"), required = true)
                OutlinedTextField(oblastId, { oblastId = it }, label = { Text("${LocalizationManager.t("proc_oblast_id")} *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subprojectId, { subprojectId = it }, label = { Text("${LocalizationManager.t("proc_subproject_id")} *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(lotId, { lotId = it }, label = { Text("${LocalizationManager.t("proc_subproject_lot_id")} *") }, modifier = Modifier.fillMaxWidth())
                InlineOptionPicker(
                    options = procurementStatuses,
                    selected = status.takeIf { it in procurementStatuses },
                    prompt = LocalizationManager.t("procurement_status"),
                    onSelect = { status = it },
                    itemLabel = LocalizationManager::procurementStatus
                )
                OutlinedTextField(tenderId, { tenderId = it }, label = { Text(LocalizationManager.t("proc_tender_id")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prozorroId, { prozorroId = it }, label = { Text(LocalizationManager.t("proc_prozorro_tender_id")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contractorUkr, { contractorUkr = it }, label = { Text(LocalizationManager.t("proc_contractor_name_uk")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contractorEng, { contractorEng = it }, label = { Text(LocalizationManager.t("proc_contractor_name_en")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contractorId, { contractorId = it }, label = { Text(LocalizationManager.t("proc_contractor_edrpou")) }, modifier = Modifier.fillMaxWidth())
                OmsDateField(contractDate, { contractDate = it }, LocalizationManager.t("proc_contract_date"), Modifier.fillMaxWidth())
                OmsDateField(contractEndDate, { contractEndDate = it }, LocalizationManager.t("proc_contract_end_date"), Modifier.fillMaxWidth())
                OutlinedTextField(duration, { numeric(it, update = { value -> duration = value }) }, label = { Text(LocalizationManager.t("proc_contract_duration")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amountUah, { numeric(it, true) { value -> amountUah = value } }, label = { Text(LocalizationManager.t("proc_contract_amount_uah")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amountEur, { numeric(it, true) { value -> amountEur = value } }, label = { Text(LocalizationManager.t("proc_contract_amount_eur")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(difference, { numeric(it, true) { value -> difference = value } }, label = { Text(LocalizationManager.t("proc_financing_difference")) }, modifier = Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = {
                    onSave(ProcurementRecordRequest(number.toInt(), batch.toInt(), oblastName, oblastId, subprojectId, lotId, status,
                        tenderId.ifBlank { null }, prozorroId.ifBlank { null }, contractorUkr.ifBlank { null }, contractorEng.ifBlank { null }, contractorId.ifBlank { null },
                        contractDate.ifBlank { null }, contractEndDate.ifBlank { null }, duration.toIntOrNull(), amountUah.toDoubleOrNull(), amountEur.toDoubleOrNull(), difference.toDoubleOrNull()?.div(100)))
                }, enabled = valid) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}
