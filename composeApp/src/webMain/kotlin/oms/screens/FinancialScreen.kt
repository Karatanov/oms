package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oms.data.ApiFinancialRecord
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.components.SortableTableHeader
import oms.components.TableActionIconButton
import oms.components.ExpandableTableText
import oms.localization.LocalizationManager
import oms.components.OmsDateField
import oms.components.toOmsDate
import oms.components.InlineOptionPicker
import oms.components.FilterDropdown
import oms.components.SearchableOptionPicker
import oms.components.currentIsoDate
import oms.components.WasmSafeOverlay
import kotlin.js.JsName
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@JsName("openFinancialImport")
external fun openFinancialImport(projectUuid: String, onComplete: (String) -> Unit)

@JsName("downloadFinancialExport")
external fun downloadFinancialExport(projectUuid: String)

private data class ProjectActRow(
    val projectUuid: String,
    val subprojectUuid: String?,
    val subprojectName: String,
    val subprojectCode: String?,
    val trancheNumber: Int,
    val act: ApiFinancialRecord
)

private enum class FinancialSort { Type, Purpose, Tranche, Subproject, SubprojectCode, ActDate, Amount, Currency, Description, Author }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true,
    requestedSubprojectUuid: String? = null,
    onRequestedSubprojectFilterConsumed: () -> Unit = {}
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    var acts by remember { mutableStateOf<List<ProjectActRow>>(emptyList()) }
    var recordTypeFilter by remember { mutableStateOf<String?>(null) }
    var paymentPurposeFilter by remember { mutableStateOf<String?>(null) }
    var trancheFilter by remember { mutableStateOf<Int?>(null) }
    var subprojectFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(FinancialSort.ActDate) }
    var ascending by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var editAct by remember { mutableStateOf<ProjectActRow?>(null) }
    var addAct by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageSize by remember { mutableStateOf(20) }
    var currentPage by remember { mutableStateOf(0) }
    var tableTopInRootPx by remember { mutableStateOf(0f) }
    val deletion = oms.components.LocalDeleteConfirmation.current
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    val tableTopPaddingPx = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(requestedSubprojectUuid) {
        requestedSubprojectUuid?.let {
            subprojectFilter = it
            onRequestedSubprojectFilterConsumed()
        }
    }

    LaunchedEffect(reloadKey) {
        loading = true; loadFailed = false
        try {
        val records = OmsApiClient.allFinancialRecords()
        // A project snapshot is necessary only to decorate actual rows.  On an
        // empty register do not wait for the much larger project hierarchy.
        if (records.isNotEmpty()) ProjectRepository.refresh()
        val projectsById = ProjectRepository.projects.associateBy { it.id }
        acts = records.mapNotNull { item ->
            projectsById[item.projectUuid]?.let { project ->
                val ancestry = generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList().asReversed()
                val subproject = ancestry.firstOrNull { it.projectType == "subproject" }
                ProjectActRow(
                    project.id,
                    subproject?.id,
                    subproject?.name ?: project.name,
                    subproject?.siteNumber ?: project.siteNumber,
                    subproject?.trancheNumber ?: project.trancheNumber,
                    item.record
                )
            }
        }.sortedByDescending { it.act.recordDate }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            loadFailed = true
        } finally { loading = false }
    }

    val completedWorksTotals = acts
        .filter { it.act.recordType == "act" }
        .groupBy { it.act.currency }
        .mapValues { (_, rows) -> rows.sumOf { it.act.amount } }
    val visibleActs = acts.filter {
        (recordTypeFilter == null || it.act.recordType == recordTypeFilter) &&
            (paymentPurposeFilter == null || it.act.paymentPurpose == paymentPurposeFilter) &&
            (trancheFilter == null || it.trancheNumber.isFinancialTranche(trancheFilter!!)) &&
            (subprojectFilter == null || it.subprojectUuid == subprojectFilter)
    }.sortedWith(compareBy<ProjectActRow> {
        when (sort) {
            FinancialSort.Type -> it.act.recordType
            FinancialSort.Purpose -> it.act.paymentPurpose
            FinancialSort.Tranche -> it.trancheNumber.toString()
            FinancialSort.Subproject -> it.subprojectName
            FinancialSort.SubprojectCode -> it.subprojectCode.orEmpty()
            FinancialSort.ActDate -> it.act.recordDate
            FinancialSort.Amount -> it.act.amount.toString().padStart(20, '0')
            FinancialSort.Currency -> it.act.currency
            FinancialSort.Description -> (it.act.description ?: it.act.milestone).orEmpty()
            FinancialSort.Author -> ""
        }
    }.let { if (ascending) it else it.reversed() })
    val pageCount = if (visibleActs.isEmpty() || pageSize == Int.MAX_VALUE) 1
    else (visibleActs.size + pageSize - 1) / pageSize
    // Filters change the data set just like on the Subprojects screen: start
    // from page one so the pagination status always describes visible rows.
    LaunchedEffect(recordTypeFilter, paymentPurposeFilter, trancheFilter, subprojectFilter, pageSize) {
        currentPage = 0
    }
    LaunchedEffect(visibleActs.size, pageSize) { currentPage = currentPage.coerceIn(0, pageCount - 1) }
    val pageRows = if (pageSize == Int.MAX_VALUE) visibleActs else visibleActs.drop(currentPage * pageSize).take(pageSize)
    val emptyRecordsMessage = when (recordTypeFilter) {
        "invoice" -> "no_invoices"
        "act" -> "no_acts"
        "payment" -> "no_payments"
        "advance" -> "no_advances"
        else -> "no_financial_records"
    }
    fun selectSort(column: FinancialSort) { if (sort == column) ascending = !ascending else { sort = column; ascending = true } }
    fun selectRecordType(type: String?) {
        recordTypeFilter = type
        scope.launch {
            delay(16)
            val target = (pageScrollState.value + tableTopInRootPx - tableTopPaddingPx)
                .roundToInt()
                .coerceIn(0, pageScrollState.maxValue)
            pageScrollState.animateScrollTo(target)
        }
    }
    fun scrollBy(delta: Float) = scope.launch { pageScrollState.animateScrollBy(delta) }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize()
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { scrollBy(-420f); true }
                    Key.DirectionDown -> { scrollBy(420f); true }
                    Key.PageUp -> { scrollBy(-720f); true }
                    Key.PageDown -> { scrollBy(720f); true }
                    Key.MoveHome -> { scope.launch { pageScrollState.animateScrollTo(0) }; true }
                    Key.MoveEnd -> { scope.launch { pageScrollState.animateScrollTo(pageScrollState.maxValue) }; true }
                    else -> false
                }
            }
            .verticalScroll(pageScrollState).padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        oms.components.PageHeading(LocalizationManager.t("financial_monitoring"), Icons.Default.AccountBalance) {
            if (canManageFinancials) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showTransferDialog = true }) {
                        Text(LocalizationManager.t("import_export_xlsx"))
                    }
                    Button(onClick = {
                        errorMessage = null
                        addAct = true
                    }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizationManager.t("add_record"))
                    }
                }
            }
        }
        if (loading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        if (loadFailed) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { reloadKey++ })

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(LocalizationManager.t("completed_works_by_acts"), style = MaterialTheme.typography.titleMedium)
                Text(completedWorksTotals.entries.joinToString(" • ") { (currency, amount) -> amount.toMoney(currency) }.ifBlank { "—" }, style = MaterialTheme.typography.headlineMedium)
            }
        }

        val financialChartRecords = acts.map { FinancialChartRecord(it.act, it.subprojectName) }
        oms.components.AdaptiveChartRow(
            first = { MonthlyPaymentsChart(financialChartRecords) },
            second = { MonthlyTechnicalSupervisionPaymentsChart(financialChartRecords) }
        )
        oms.components.AdaptiveChartRow(
            first = { MonthlyEquipmentPaymentsChart(financialChartRecords) },
            second = { MonthlyEngineerConsultantPaymentsChart(financialChartRecords) }
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(LocalizationManager.t("financial_records"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            // Keep page controls immediately visible, like the Subprojects
            // registry. A second copy remains below the table for long lists.
            FinancialPagination(pageSize, currentPage, pageCount, visibleActs.size, { pageSize = it }) {
                currentPage = it.coerceIn(0, pageCount - 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            FilterDropdown(
                label = LocalizationManager.t("type"),
                options = listOf("invoice", "act", "payment", "advance"),
                selected = recordTypeFilter,
                onSelect = ::selectRecordType,
                itemLabel = { LocalizationManager.t("record_type_$it") }
            )
            FilterDropdown(
                label = LocalizationManager.t("payment_purpose"),
                options = listOf("works", "equipment", "technical_supervision", "engineer_consultant"),
                selected = paymentPurposeFilter,
                onSelect = { paymentPurposeFilter = it },
                itemLabel = { LocalizationManager.t("payment_purpose_$it") }
            )
            FilterDropdown(
                label = LocalizationManager.t("tranche"),
                options = listOf(1, 2),
                selected = trancheFilter,
                onSelect = { trancheFilter = it },
                itemLabel = { it.financialTrancheCode() }
            )
            OutlinedButton(onClick = {
                recordTypeFilter = null
                paymentPurposeFilter = null
                trancheFilter = null
                subprojectFilter = null
            }) { Text(LocalizationManager.t("reset_filters")) }
        }
        oms.components.ScrollableTable(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { tableTopInRootPx = it.positionInRoot().y },
            pageScrollState = pageScrollState,
            header = { FinancialTableHeader(sort, ascending, ::selectSort); HorizontalDivider() }
        ) {
                    if (!loading && !loadFailed && visibleActs.isEmpty()) Text(LocalizationManager.t(emptyRecordsMessage))
                    pageRows.forEach { row ->
                        Row(Modifier.width(1_481.dp).padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(LocalizationManager.t("record_type_${row.act.recordType}"), Modifier.width(95.dp))
                            Box(Modifier.width(210.dp)) { FinancialPaymentPurposeBadge(row.act.recordType, row.act.paymentPurpose) }
                            Text(row.trancheNumber.financialTrancheCode(), Modifier.width(95.dp))
                            ExpandableTableText(row.subprojectName, Modifier.width(200.dp))
                            Text(row.subprojectCode ?: "—", Modifier.width(160.dp))
                            Text(row.act.recordDate.toOmsDate(), Modifier.width(105.dp))
                            Text(row.act.amount.toMoney("").trim(), Modifier.width(130.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text(row.act.currency, Modifier.width(65.dp))
                            Text(row.act.description ?: row.act.milestone ?: "—", Modifier.width(250.dp))
                            Text("—", Modifier.width(75.dp))
                            if (canManageFinancials) {
                                TableActionIconButton(LocalizationManager.t("edit_financial_record_tooltip"), Icons.Default.Edit) { errorMessage = null; editAct = row }
                                TableActionIconButton(LocalizationManager.t("delete_financial_record_tooltip"), Icons.Default.Delete) {
                                    deletion.show(row.act.referenceNumber) { scope.launch {
                                        if (OmsApiClient.deleteFinancialRecord(row.projectUuid, row.act.uuid)) reloadKey++
                                        else errorMessage = LocalizationManager.t("error_delete_act")
                                    } }
                                }
                            } else Spacer(Modifier.width(96.dp))
                        }
                        HorizontalDivider()
                    }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FinancialPagination(pageSize, currentPage, pageCount, visibleActs.size, { pageSize = it }) {
                currentPage = it.coerceIn(0, pageCount - 1)
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    Surface(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).zIndex(10f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp, pageScrollState, -1)
            oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown, pageScrollState, 1)
        }
    }
    if (addAct || editAct != null) { WasmSafeOverlay(onDismiss = { addAct = false; editAct = null }, errorMessage = errorMessage) {
            ActEditorDialog(editAct, ProjectRepository.projects, {
                ProjectRepository.refresh(force = ProjectRepository.projects.isEmpty())
                ProjectRepository.projects
            }, { addAct = false; editAct = null }) { projectUuid, request ->
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
    if (showTransferDialog) { WasmSafeOverlay(onDismiss = { showTransferDialog = false }, errorMessage = errorMessage) {
            FinancialTransferDialog(
                projects = ProjectRepository.projects,
                loadProjectsOnOpen = {
                    ProjectRepository.refresh(force = ProjectRepository.projects.isEmpty())
                    ProjectRepository.projects
                },
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
    loadProjectsOnOpen: suspend () -> List<oms.model.Project>,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onExport: (String) -> Unit
) {
    var availableProjects by remember { mutableStateOf(projects) }
    var projectUuid by remember { mutableStateOf(projects.firstOrNull { it.projectType.equals("project", true) }?.id) }
    Card(Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(LocalizationManager.t("financial_transfer_title"), style = MaterialTheme.typography.titleLarge)
            FinancialProjectTargetSelector(availableProjects, projectUuid, {
                loadProjectsOnOpen().also { availableProjects = it }
            }) { projectUuid = it }
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
private fun ActEditorDialog(
    existing: ProjectActRow?,
    projects: List<oms.model.Project>,
    loadProjectsOnOpen: suspend () -> List<oms.model.Project>,
    onDismiss: () -> Unit,
    onSave: (String, oms.data.FinancialRecordRequest) -> Unit
) {
    var availableProjects by remember { mutableStateOf(projects) }
    var projectUuid by remember { mutableStateOf(existing?.projectUuid) }
    var reference by remember { mutableStateOf(existing?.act?.referenceNumber ?: "") }
    var recordType by remember { mutableStateOf(existing?.act?.recordType ?: "act") }
    var amount by remember { mutableStateOf(existing?.act?.amount?.toString() ?: "") }
    var currency by remember { mutableStateOf(existing?.act?.currency ?: "EUR") }
    var date by remember { mutableStateOf(existing?.act?.recordDate ?: currentIsoDate()) }
    var description by remember { mutableStateOf(existing?.act?.description ?: existing?.act?.milestone ?: "") }
    var paymentPurpose by remember { mutableStateOf(existing?.act?.paymentPurpose ?: "works") }
    var basisActs by remember { mutableStateOf<List<ApiFinancialRecord>>(emptyList()) }
    var loadingBasisActs by remember { mutableStateOf(false) }
    var basisProjectUuid by remember { mutableStateOf<String?>(null) }
    fun selectedSubprojectUuid(targetUuid: String?): String? {
        val byId = availableProjects.associateBy { it.id }
        return byId[targetUuid]?.let { target ->
            generateSequence(target) { current -> current.parentProjectUuid?.let(byId::get) }
                .firstOrNull { it.projectType.equals("subproject", true) }
                ?.id
        }
    }
    LaunchedEffect(recordType, projectUuid, availableProjects) {
        val subprojectUuid = selectedSubprojectUuid(projectUuid)
        basisProjectUuid = subprojectUuid
        if (recordType !in setOf("payment", "advance") || subprojectUuid == null) {
            basisActs = emptyList()
            loadingBasisActs = false
            return@LaunchedEffect
        }
        loadingBasisActs = true
        basisActs = runCatching { OmsApiClient.financials(subprojectUuid).data }
            .getOrDefault(emptyList())
            .filter { it.recordType.equals("act", true) }
        loadingBasisActs = false
    }
    val parsedAmount = amount.replace(',', '.').toDoubleOrNull()
    val valid = projectUuid != null && reference.isNotBlank() && parsedAmount?.let { it.isFinite() && it > 0 } == true && date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.padding(20.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(if (existing == null) LocalizationManager.t("add_financial_record") else LocalizationManager.t("edit_financial_record"), style = MaterialTheme.typography.titleLarge)
            FinancialProjectTargetSelector(availableProjects, projectUuid, {
                loadProjectsOnOpen().also { availableProjects = it }
            }) { projectUuid = it }
            OutlinedTextField(reference, { reference = it }, label = { Text(LocalizationManager.t("reference_number")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("invoice", "act", "payment", "advance").forEach { type -> FilterChip(selected = recordType == type, onClick = { recordType = type }, label = { Text(LocalizationManager.t("record_type_$type")) }) }
            }
            if (recordType in setOf("payment", "advance")) {
                Text(LocalizationManager.t("payment_purpose"), style = MaterialTheme.typography.labelLarge)
                InlineOptionPicker(options = listOf("works", "equipment", "technical_supervision", "engineer_consultant"),
                    selected = paymentPurpose, prompt = LocalizationManager.t("payment_purpose"), onSelect = { paymentPurpose = it },
                    itemLabel = { LocalizationManager.t("payment_purpose_$it") })
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { entered ->
                    val filtered = entered
                        .filter { it.isDigit() || it == '.' || it == ',' }
                        .replace(',', '.')
                    amount = if (filtered.matches(Regex("\\d{0,13}(\\.\\d{0,2})?"))) filtered else amount
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
            if (recordType in setOf("payment", "advance") && basisProjectUuid != null && basisActs.isNotEmpty()) {
                val selectedBasis = basisActs.firstOrNull { it.referenceNumber == description }
                SearchableOptionPicker(
                    options = basisActs,
                    selected = selectedBasis,
                    label = LocalizationManager.t("payment_basis"),
                    onSelect = { act -> description = act.referenceNumber },
                    itemLabel = { act -> "${act.referenceNumber} · ${act.recordDate.toOmsDate()} · ${act.amount.toMoney(act.currency)}" }
                )
            } else {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(LocalizationManager.t(if (recordType in setOf("payment", "advance")) "payment_basis" else "description")) },
                    placeholder = if (recordType in setOf("payment", "advance")) { { Text(LocalizationManager.t("payment_basis_hint")) } } else null,
                    supportingText = if (recordType in setOf("payment", "advance") && loadingBasisActs) {
                        { Text(LocalizationManager.t("loading_records")) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (recordType in setOf("payment", "advance")) 2 else 1
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                Button(onClick = { onSave(projectUuid!!, oms.data.FinancialRecordRequest(recordType, reference, parsedAmount!!, currency, date, description = description.ifBlank { null }, milestone = null, paymentPurpose = paymentPurpose)) }, enabled = valid) { Text(LocalizationManager.t("save")) }
            }
        }
    }
}

/** A financial record may be attached to any hierarchy level; the deepest selected level is saved. */
@Composable
private fun FinancialProjectTargetSelector(
    projects: List<oms.model.Project>,
    selectedTargetUuid: String?,
    loadProjectsOnOpen: suspend () -> List<oms.model.Project>,
    onTargetSelect: (String?) -> Unit
) {
    var availableProjects by remember { mutableStateOf(projects) }
    var rootUuid by remember { mutableStateOf<String?>(null) }
    var subprojectUuid by remember { mutableStateOf<String?>(null) }
    var partUuid by remember { mutableStateOf<String?>(null) }

    fun restoreHierarchy(targetUuid: String?, source: List<oms.model.Project>) {
        val byId = source.associateBy { it.id }
        val selected = byId[targetUuid] ?: return
        val ancestry = generateSequence(selected) { current -> current.parentProjectUuid?.let(byId::get) }.toList().asReversed()
        rootUuid = ancestry.firstOrNull { it.projectType.equals("project", true) }?.id
        subprojectUuid = ancestry.firstOrNull { it.projectType.equals("subproject", true) }?.id
        partUuid = ancestry.firstOrNull { it.projectType.equals("subproject_part", true) }?.id
    }

    LaunchedEffect(projects) {
        if (projects.isNotEmpty()) {
            availableProjects = projects
            if (rootUuid == null) {
                restoreHierarchy(selectedTargetUuid, projects)
                if (rootUuid == null) {
                    rootUuid = projects.filter { it.projectType.equals("project", true) }.singleOrNull()?.id
                }
            }
        }
    }

    val rootProjects = availableProjects.filter { it.projectType.equals("project", true) }
    fun subprojectsForRoot(source: List<oms.model.Project>, selectedRoot: String?): List<oms.model.Project> {
        if (selectedRoot == null) return source.filter { it.projectType.equals("subproject", true) }
        val onlyRootUuid = source.filter { it.projectType.equals("project", true) }.singleOrNull()?.id
        return source.filter {
            it.projectType.equals("subproject", true) &&
                (it.parentProjectUuid == selectedRoot || (onlyRootUuid == selectedRoot && it.parentProjectUuid == null))
        }
    }
    val subprojects = subprojectsForRoot(availableProjects, rootUuid)
    val parts = availableProjects.filter { it.projectType.equals("subproject_part", true) && it.parentProjectUuid == subprojectUuid }
    suspend fun loadAndRemember(): List<oms.model.Project> = loadProjectsOnOpen().also { loaded ->
        availableProjects = loaded
        if (rootUuid == null) restoreHierarchy(selectedTargetUuid, loaded)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FinancialProjectLevelDropdown(
            LocalizationManager.t("select_project"), rootProjects, rootUuid,
            onSelect = { selected -> rootUuid = selected; subprojectUuid = null; partUuid = null; onTargetSelect(null) },
            loadOptionsOnOpen = { loadAndRemember().filter { it.projectType.equals("project", true) } }
        )
        FinancialProjectLevelDropdown(
            LocalizationManager.t("select_subproject"), subprojects, subprojectUuid,
            onSelect = { selected ->
                subprojectUuid = selected
                partUuid = null
                availableProjects.firstOrNull { it.id == selected }?.parentProjectUuid?.let { rootUuid = it }
                onTargetSelect(selected)
            },
            // Keep this field interactive even while the hierarchy is not in
            // memory yet. Opening it loads the projects and can infer the root
            // from the selected subproject.
            enabled = true,
            searchable = true,
            loadOptionsOnOpen = {
                val loaded = loadAndRemember()
                val selectedRoot = rootUuid ?: loaded
                    .filter { it.projectType.equals("project", true) }
                    .singleOrNull()?.id
                if (rootUuid == null && selectedRoot != null) rootUuid = selectedRoot
                subprojectsForRoot(loaded, selectedRoot)
            }
        )
        FinancialProjectLevelDropdown(
            LocalizationManager.t("select_subproject_part"), parts, partUuid,
            onSelect = { selected -> partUuid = selected; onTargetSelect(selected) },
            enabled = subprojectUuid != null,
            loadOptionsOnOpen = {
                loadAndRemember().filter {
                    it.projectType.equals("subproject_part", true) && it.parentProjectUuid == subprojectUuid
                }
            }
        )
    }
}

@Composable
private fun FinancialProjectLevelDropdown(
    label: String,
    options: List<oms.model.Project>,
    selectedUuid: String?,
    onSelect: (String?) -> Unit,
    enabled: Boolean = true,
    searchable: Boolean = false,
    loadOptionsOnOpen: (suspend () -> List<oms.model.Project>)? = null
) {
    val selected = options.firstOrNull { it.id == selectedUuid }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        val itemLabel: (oms.model.Project) -> String = { project ->
                if (project.siteNumber.equals(project.name, ignoreCase = true)) project.name
                else "${project.siteNumber} — ${project.name}"
            }
        if (searchable) {
            SearchableOptionPicker(
                options, selected, label, { onSelect(it.id) }, itemLabel,
                enabled = enabled, loadOptionsOnInput = loadOptionsOnOpen
            )
        } else {
            InlineOptionPicker(
                options = options, selected = selected, prompt = label, onSelect = { onSelect(it.id) },
                itemLabel = itemLabel, enabled = enabled, loadOptionsOnOpen = loadOptionsOnOpen
            )
        }
    }
}

@Composable
private fun FinancialTableHeader(sort: FinancialSort, ascending: Boolean, onSort: (FinancialSort) -> Unit) {
    Row(Modifier.width(1_481.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("type"), sort == FinancialSort.Type, ascending, { onSort(FinancialSort.Type) }, Modifier.width(95.dp))
        SortableTableHeader(LocalizationManager.t("payment_purpose"), sort == FinancialSort.Purpose, ascending, { onSort(FinancialSort.Purpose) }, Modifier.width(210.dp))
        SortableTableHeader(LocalizationManager.t("tranche"), sort == FinancialSort.Tranche, ascending, { onSort(FinancialSort.Tranche) }, Modifier.width(95.dp))
        SortableTableHeader(LocalizationManager.t("subproject"), sort == FinancialSort.Subproject, ascending, { onSort(FinancialSort.Subproject) }, Modifier.width(200.dp))
        SortableTableHeader(LocalizationManager.t("subproject_code"), sort == FinancialSort.SubprojectCode, ascending, { onSort(FinancialSort.SubprojectCode) }, Modifier.width(160.dp))
        SortableTableHeader(LocalizationManager.t("date"), sort == FinancialSort.ActDate, ascending, { onSort(FinancialSort.ActDate) }, Modifier.width(105.dp))
        SortableTableHeader(LocalizationManager.t("amount"), sort == FinancialSort.Amount, ascending, { onSort(FinancialSort.Amount) }, Modifier.width(130.dp), numeric = true)
        SortableTableHeader(LocalizationManager.t("currency_short"), sort == FinancialSort.Currency, ascending, { onSort(FinancialSort.Currency) }, Modifier.width(65.dp))
        SortableTableHeader(LocalizationManager.t("description"), sort == FinancialSort.Description, ascending, { onSort(FinancialSort.Description) }, Modifier.width(250.dp))
        SortableTableHeader(LocalizationManager.t("author"), sort == FinancialSort.Author, ascending, { onSort(FinancialSort.Author) }, Modifier.width(75.dp))
        Box(Modifier.width(96.dp).height(52.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(LocalizationManager.t("actions"), style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun FinancialPagination(
    pageSize: Int,
    currentPage: Int,
    pageCount: Int,
    total: Int,
    onPageSize: (Int) -> Unit,
    onPage: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(LocalizationManager.t("rows_per_page"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        InlineOptionPicker(
            options = listOf(20, 50, 100, Int.MAX_VALUE),
            selected = pageSize,
            prompt = LocalizationManager.t("rows_per_page"),
            onSelect = onPageSize,
            itemLabel = { if (it == Int.MAX_VALUE) LocalizationManager.t("all") else it.toString() },
            fillWidth = false
        )
        Spacer(Modifier.width(12.dp))
        Text(
            LocalizationManager.t("page_of")
                .replace("{page}", (currentPage + 1).toString())
                .replace("{pages}", pageCount.toString())
                .replace("{total}", total.toString()),
            style = MaterialTheme.typography.bodySmall
        )
        TableActionIconButton(LocalizationManager.t("previous_page"), Icons.Default.KeyboardArrowLeft) { onPage(currentPage - 1) }
        TableActionIconButton(LocalizationManager.t("next_page"), Icons.Default.KeyboardArrowRight) { onPage(currentPage + 1) }
    }
}

private fun Int.isFinancialTranche(filter: Int): Boolean = when (filter) {
    1 -> this == 1 || this == 8
    2 -> this == 2 || this == 9
    else -> this == filter
}

/** The database stores the tranche as a number; the register deliberately exposes only its A/B code. */
private fun Int.financialTrancheCode(): String = if (this == 2 || this == 9) "B" else "A"

@Composable
private fun FinancialPaymentPurposeBadge(recordType: String, purpose: String) {
    if (recordType !in setOf("payment", "advance")) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (labelKey, color) = when (purpose) {
        "equipment" -> "payment_purpose_equipment" to oms.theme.OmsColors.Warning
        "technical_supervision" -> "payment_purpose_technical_supervision" to oms.theme.OmsColors.Success
        "engineer_consultant" -> "payment_purpose_engineer_consultant" to oms.theme.OmsColors.Purple
        else -> "payment_purpose_works" to MaterialTheme.colorScheme.primary
    }
    oms.components.OmsBadge(LocalizationManager.t(labelKey), color)
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

private fun Double.toMoney(currency: String): String = "${formatUiAmount()} $currency"

/** Locale-independent formatter that also works in Kotlin/Wasm. */
private fun Double.formatUiAmount(): String {
    val scaled = (this * 100).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = kotlin.math.abs(scaled)
    val whole = absolute / 100
    val fraction = absolute % 100
    val groupedWhole = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return if (fraction == 0L) "$sign$groupedWhole" else "$sign$groupedWhole,${fraction.toString().padStart(2, '0').trimEnd('0')}"
}
