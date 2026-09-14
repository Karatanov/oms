package oms.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import oms.data.ApiProcurementRecord
import oms.data.ApiDashboardMetric
import oms.data.OmsApiClient
import oms.data.ProcurementRecordRequest
import oms.components.OmsDateField
import oms.components.TableActionIconButton
import oms.components.SortableTableHeader
import oms.components.InlineOptionPicker
import oms.components.UkraineRegionAutocomplete
import oms.components.currentIsoDate
import oms.components.WasmSafeOverlay
import oms.components.toOmsDate
import oms.components.localizedUkraineRegion
import oms.localization.Language
import oms.localization.LocalizationManager
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProcurementScreen(
    canManageProcurements: Boolean,
    requestedStatusFilter: String? = null,
    onRequestedStatusFilterConsumed: () -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    var oblastFilter by remember { mutableStateOf<String?>(null) }
    var subprojectFilter by remember { mutableStateOf<String?>(null) }
    var contractTypeFilter by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf<String?>(null) }
    var signedContractMonthFilter by remember { mutableStateOf<String?>(null) }
    var records by remember { mutableStateOf<List<ApiProcurementRecord>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var editorRecord by remember { mutableStateOf<ApiProcurementRecord?>(null) }
    var creating by remember { mutableStateOf(false) }
    var recordPendingDeletion by remember { mutableStateOf<ApiProcurementRecord?>(null) }
    var pageSize by remember { mutableStateOf(20) }
    var currentPage by remember { mutableStateOf(0) }
    var sortColumnIndex by remember { mutableStateOf(0) }
    var sortAscending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val contentScrollState = rememberScrollState()
    var tableTopInRootPx by remember { mutableStateOf(0f) }
    val tableTopPaddingPx = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(requestedStatusFilter) {
        requestedStatusFilter?.let {
            statusFilter = it
            onRequestedStatusFilterConsumed()
        }
    }
    LaunchedEffect(reloadKey) {
        loadError = null
        runCatching { OmsApiClient.procurements() }
            .onSuccess { records = it.sortedWith(compareBy({ record -> record.batchId }, { record -> record.recordNumber })) }
            .onFailure { loadError = LocalizationManager.t("procurement_load_error") }
    }
    fun applySignedContractMonthFilter(month: String) {
        signedContractMonthFilter = month
        statusFilter = procurementStatuses[3]
        scope.launch {
            kotlinx.coroutines.yield()
            contentScrollState.animateScrollTo(
                (contentScrollState.value + tableTopInRootPx - tableTopPaddingPx)
                    .toInt()
                    .coerceIn(0, contentScrollState.maxValue)
            )
        }
    }
    val procurementStatusMetrics = records?.let { loaded ->
        procurementStatuses.map { status ->
            ApiDashboardMetric(
                status,
                loaded.asSequence()
                    .filter { sameProcurementStatus(it.purchaseStatus, status) }
                    .map { it.subProjectId }
                    .distinct()
                    .count()
                    .toLong()
            )
        }
    }.orEmpty()
    val signedContractMetrics = records.orEmpty()
        .asSequence()
        .filter { sameProcurementStatus(it.purchaseStatus, procurementStatuses[3]) }
        .mapNotNull { (it.contractDate ?: it.estimatedContractDate)?.takeIf { date -> date.length >= 7 }?.take(7) }
        .groupingBy { it }
        .eachCount()
        .map { ApiDashboardMetric(it.key, it.value.toLong()) }
        .sortedBy { it.label }
    fun scrollBy(delta: Float) = scope.launch { contentScrollState.animateScrollBy(delta) }
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
            .padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 24.dp)
    ) {
        oms.components.PageHeading(LocalizationManager.t("procurement_title"), Icons.Default.ShoppingCart) {
            if (canManageProcurements) Button(onClick = { error = null; creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("add_procurement_record")) }
        }
        Spacer(Modifier.height(8.dp))
        Text(LocalizationManager.t("procurement_subtitle"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        oms.components.AdaptiveChartRow(
            first = { MetricsListChart("procurement_status_chart", procurementStatusMetrics) },
            second = {
                MetricsChart(
                    "signed_construction_contracts",
                    "signed_construction_contracts_hint",
                    signedContractMetrics,
                    centerYearLabels = true,
                    onItemClick = ::applySignedContractMonthFilter
                )
            }
        )
        Spacer(Modifier.height(16.dp))
        val visibleRecords = records.orEmpty().filter { record ->
            (oblastFilter == null || record.oblastName == oblastFilter) &&
            (subprojectFilter == null || record.subProjectId == subprojectFilter) &&
            (contractTypeFilter == null || record.sourceContractType == contractTypeFilter) &&
            (statusFilter?.let { sameProcurementStatus(record.purchaseStatus, it) } ?: true) &&
            (signedContractMonthFilter == null || (record.contractDate ?: record.estimatedContractDate)?.take(7) == signedContractMonthFilter) &&
            (search.isBlank() || listOf(record.subProjectId, record.subProjectLotId.orEmpty(), record.oblastName, record.promotorName.orEmpty(), record.subprojectNameUk.orEmpty(), record.subprojectNameEn.orEmpty(), record.procurementId.orEmpty()).any { it.contains(search, true) })
        }.sortedWith(compareBy<ApiProcurementRecord> { it.sortKey(sortColumnIndex) }
            .let { comparator -> if (sortAscending) comparator else comparator.reversed() })
        fun selectSort(columnIndex: Int) {
            if (sortColumnIndex == columnIndex) sortAscending = !sortAscending
            else {
                sortColumnIndex = columnIndex
                sortAscending = true
            }
        }
        LaunchedEffect(search, oblastFilter, subprojectFilter, contractTypeFilter, statusFilter, signedContractMonthFilter, pageSize) { currentPage = 0 }
        val pageCount = if (visibleRecords.isEmpty() || pageSize == Int.MAX_VALUE) 1 else (visibleRecords.size + pageSize - 1) / pageSize
        if (currentPage >= pageCount) currentPage = (pageCount - 1).coerceAtLeast(0)
        val pageRecords = if (pageSize == Int.MAX_VALUE) visibleRecords else visibleRecords.drop(currentPage * pageSize).take(pageSize)
        if (records != null && visibleRecords.isEmpty()) oms.components.ContentState(LocalizationManager.t("no_search_results"))
        when {
            loadError != null -> oms.components.ContentState(loadError!!, error = true, onRetry = { reloadKey++ })
            records == null -> CircularProgressIndicator()
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        label = { Text(LocalizationManager.t("procurement_search")) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.weight(1f).widthIn(min = 280.dp)
                    )
                    ProcurementPagination(
                        pageSize = pageSize,
                        currentPage = currentPage,
                        pageCount = pageCount,
                        total = visibleRecords.size,
                        onPageSize = { pageSize = it },
                        onPage = { currentPage = it.coerceIn(0, pageCount - 1) }
                    )
                }
                signedContractMonthFilter?.let { month ->
                    FilterChip(
                        selected = true,
                        onClick = { signedContractMonthFilter = null; statusFilter = null },
                        label = { Text("${LocalizationManager.t("signed_construction_contracts")}: $month") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.onGloballyPositioned { tableTopInRootPx = it.positionInRoot().y }) {
                    ProcurementTable(
                        pageRecords, records.orEmpty(), oblastFilter, { oblastFilter = it }, subprojectFilter, { subprojectFilter = it },
                        contractTypeFilter, { contractTypeFilter = it }, statusFilter, { statusFilter = it },
                        canManageProcurements, { error = null; editorRecord = it }, { error = null; recordPendingDeletion = it },
                        contentScrollState, sortColumnIndex, sortAscending, ::selectSort
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ProcurementPagination(
                        pageSize = pageSize,
                        currentPage = currentPage,
                        pageCount = pageCount,
                        total = visibleRecords.size,
                        onPageSize = { pageSize = it },
                        onPage = { currentPage = it.coerceIn(0, pageCount - 1) }
                    )
                }
            }
        }
    }
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp, contentScrollState, -1)
        oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown, contentScrollState, 1)
    }
    if (creating) WasmSafeOverlay(onDismiss = { creating = false }, errorMessage = error) {
            ProcurementEditorDialog(null, onDismiss = { creating = false }) { request ->
            scope.launch { runCatching { OmsApiClient.createProcurement(request) }
                .onSuccess { records = (records.orEmpty() + it).sortedBy { record -> record.recordNumber }; creating = false }
                .onFailure { error = LocalizationManager.t("error_create_procurement").replace("{message}", it.message.orEmpty()) } }
            }
        }
    editorRecord?.let { existing -> WasmSafeOverlay(onDismiss = { editorRecord = null }, errorMessage = error) {
            ProcurementEditorDialog(existing, onDismiss = { editorRecord = null }) { request ->
            scope.launch { runCatching { OmsApiClient.updateProcurement(existing.id, request) }
                .onSuccess { saved -> records = records.orEmpty().map { if (it.id == saved.id) saved else it }; editorRecord = null }
                .onFailure { error = LocalizationManager.t("error_update_procurement").replace("{message}", it.message.orEmpty()) } }
            }
        }
        }
    recordPendingDeletion?.let { record -> WasmSafeOverlay(onDismiss = { recordPendingDeletion = null }, errorMessage = error) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
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
private fun ProcurementPagination(
    pageSize: Int,
    currentPage: Int,
    pageCount: Int,
    total: Int,
    onPageSize: (Int) -> Unit,
    onPage: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProcurementTable(
    records: List<ApiProcurementRecord>,
    allRecords: List<ApiProcurementRecord>,
    oblastFilter: String?,
    onOblastChange: (String?) -> Unit,
    subprojectFilter: String?,
    onSubprojectChange: (String?) -> Unit,
    contractTypeFilter: String?,
    onContractTypeChange: (String?) -> Unit,
    statusFilter: String?,
    onStatusChange: (String?) -> Unit,
    canManage: Boolean,
    onEdit: (ApiProcurementRecord) -> Unit,
    onDelete: (ApiProcurementRecord) -> Unit,
    pageScrollState: ScrollState,
    sortColumnIndex: Int,
    sortAscending: Boolean,
    onSort: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        oms.components.ScrollableTable(pageScrollState = pageScrollState, header = {
                ProcurementFilters(allRecords, oblastFilter, onOblastChange, subprojectFilter, onSubprojectChange,
                    contractTypeFilter, onContractTypeChange, statusFilter, onStatusChange, canManage)
                ProcurementRow(procurementHeaderLabels(), showActions = canManage, isHeader = true,
                    sortColumnIndex = sortColumnIndex, sortAscending = sortAscending, onSort = onSort)
                HorizontalDivider()
            }
        ) {
            records.forEach { record ->
                ProcurementRow(record.displayValues(), record.takeIf { canManage }, onEdit, onDelete, showActions = canManage)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ProcurementFilters(
    records: List<ApiProcurementRecord>,
    oblastFilter: String?,
    onOblastChange: (String?) -> Unit,
    subprojectFilter: String?,
    onSubprojectChange: (String?) -> Unit,
    contractTypeFilter: String?,
    onContractTypeChange: (String?) -> Unit,
    statusFilter: String?,
    onStatusChange: (String?) -> Unit,
    showActions: Boolean
) {
    Row(
        Modifier.width((columnWidths.sum() + if (showActions) 96 else 0).dp).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showActions) Spacer(Modifier.width(96.dp))
        columnWidths.forEachIndexed { index, width ->
            Box(Modifier.width(width.dp).padding(horizontal = 6.dp)) {
                when (index) {
                    2 -> InlineOptionPicker(
                        records.map { it.oblastName }.filter(String::isNotBlank).distinct().sorted(), oblastFilter,
                        LocalizationManager.t("proc_oblast_name"), onOblastChange,
                        ::localizedUkraineRegion,
                        clearLabel = LocalizationManager.t("all"), onClear = { onOblastChange(null) }
                    )
                    6 -> InlineOptionPicker(
                        records.map { it.subProjectId }.filter(String::isNotBlank).distinct().sorted(), subprojectFilter,
                        LocalizationManager.t("proc_subproject_id"), onSubprojectChange,
                        clearLabel = LocalizationManager.t("all"), onClear = { onSubprojectChange(null) }
                    )
                    8 -> InlineOptionPicker(
                        records.mapNotNull { it.sourceContractType?.takeIf(String::isNotBlank) }.distinct().sorted(),
                        contractTypeFilter, LocalizationManager.t("proc_contract_type"), onContractTypeChange,
                        clearLabel = LocalizationManager.t("all"), onClear = { onContractTypeChange(null) }
                    )
                    22 -> InlineOptionPicker(
                        procurementStatuses, statusFilter, LocalizationManager.t("procurement_status"), onStatusChange,
                        LocalizationManager::procurementStatus,
                        clearLabel = LocalizationManager.t("all"), onClear = { onStatusChange(null) }
                    )
                }
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
    isHeader: Boolean = false,
    sortColumnIndex: Int = -1,
    sortAscending: Boolean = true,
    onSort: (Int) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    Row(
        Modifier.width((columnWidths.sum() + if (showActions) 96 else 0).dp)
            .padding(vertical = if (isHeader) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showActions) {
            if (isHeader) {
                Text(
                    LocalizationManager.t("actions"),
                    Modifier.width(96.dp).padding(horizontal = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else if (record != null) {
                Box(Modifier) {
                TableActionIconButton(LocalizationManager.t("edit_procurement_record"), Icons.Default.Edit) { onEdit(record) }
                }
                Box(Modifier) {
                TableActionIconButton(LocalizationManager.t("delete_procurement_record"), Icons.Default.Delete) { onDelete(record) }
                }
            }
        }
        values.take(columnWidths.size).zip(columnWidths).forEachIndexed { index, (value, width) ->
            if (isHeader) {
                SortableTableHeader(value, index == sortColumnIndex, sortAscending, { onSort(index) }, Modifier.width(width.dp))
                return@forEachIndexed
            }
            if (!isHeader && index == 22) Box(Modifier.width(width.dp).padding(horizontal = 6.dp)) {
                oms.components.OmsBadge(value, procurementStatusColor(record?.purchaseStatus ?: value))
            } else if (!isHeader && index == 10 && value.isNotBlank()) {
                Text(
                    value,
                    Modifier.width(width.dp).padding(horizontal = 6.dp)
                        .clickable { uriHandler.openUri(value.toProzorroTenderUrl()) }
                        .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            } else Text(
                value,
                Modifier.width(width.dp).padding(horizontal = 6.dp),
                maxLines = if (isHeader) 1 else Int.MAX_VALUE,
                overflow = if (isHeader) TextOverflow.Ellipsis else TextOverflow.Clip,
                style = if (isHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** Keeps cell rendering and sorting aligned with every displayed procurement column. */
private fun ApiProcurementRecord.displayValues(): List<String> = listOf(
    recordNumber.toString(), if (batchId == 8) "A" else if (batchId == 9) "B" else batchId.toString(), localizedUkraineRegion(oblastName), oblastId,
    promotorName.orEmpty(), if (LocalizationManager.currentLanguage == Language.EN) subprojectNameEn ?: subprojectNameUk.orEmpty() else subprojectNameUk ?: subprojectNameEn.orEmpty(),
    subProjectId, spId.orEmpty(), sourceContractType.orEmpty(), sourceType.orEmpty(), procurementId.orEmpty(),
    subprojectTotalCostUah.format(0), subprojectEibFinancingUah.format(0), subprojectLocalFinancingUah.format(0), estimatedTotalEur.format(2),
    procurementMethod.orEmpty(), tenderDocumentType.orEmpty(), publishedInOjeu.orEmpty(), estimatedProzorroDate.toOmsDate(), estimatedBidSubmissionDate.toOmsDate(),
    estimatedContractDate.toOmsDate(), estimatedContractEndDate.toOmsDate(), LocalizationManager.procurementStatus(purchaseStatus.orEmpty()),
    localFinancingPct?.let { "${it.format(2)}%" }.orEmpty(), comments.orEmpty()
)

/** Keeps procurement states visually distinguishable without depending on the selected UI language. */
private fun procurementStatusColor(status: String) = when {
    status.contains("договір укладено", ignoreCase = true) ||
        status.contains("contract signed", ignoreCase = true) -> oms.theme.OmsColors.Success
    status.contains("закупівля триває", ignoreCase = true) ||
        status.contains("tender ongoing", ignoreCase = true) -> oms.theme.OmsColors.Information
    status.contains("повідомлення про намір", ignoreCase = true) ||
        status.contains("contract award notice", ignoreCase = true) -> oms.theme.OmsColors.Warning
    status.contains("відмінено", ignoreCase = true) ||
        status.contains("розірвано", ignoreCase = true) ||
        status.contains("cancelled", ignoreCase = true) ||
        status.contains("terminated", ignoreCase = true) -> oms.theme.OmsColors.Danger
    else -> oms.theme.OmsColors.Neutral
}

private fun ApiProcurementRecord.sortKey(columnIndex: Int): String = when (columnIndex) {
    0 -> recordNumber.toString().padStart(12, '0')
    1 -> batchId.toString().padStart(12, '0')
    11 -> subprojectTotalCostUah.sortKey()
    12 -> subprojectEibFinancingUah.sortKey()
    13 -> subprojectLocalFinancingUah.sortKey()
    14 -> estimatedTotalEur.sortKey()
    23 -> localFinancingPct.sortKey()
    else -> displayValues().getOrElse(columnIndex) { "" }.lowercase()
}

private fun Double?.sortKey(): String = this?.let { value ->
    value.toString().padStart(28, '0')
} ?: ""

private fun procurementHeaderLabels(): List<String> = if (LocalizationManager.currentLanguage == Language.EN) listOf(
    "No.", "Tranche", "Region", "Region ID", "Promotor", "Subproject name", "Subproject ID", "SP ID", "Contract type", "Type", "Procurement ID",
    "Subproject cost, UAH", "EIB financing, UAH", "Local financing, UAH", "Estimated total, EUR", "Procurement method", "TD type", "Published in OJEU",
    "Est. PROZORRO publication", "Est. bid submission", "Est. contract signing", "Est. contract end", "Procurement status", "Local co-financing", "Comments"
) else listOf(
    "№", "Транш", "Область", "Код області", "Бенефіціар", "Назва субпроєкту", "Код субпроєкту", "SP ID", "Тип контракту", "Тип", "ID закупівлі",
    "Вартість субпроєкту, грн", "Фінансування ЄІБ, грн", "Місцеве фінансування, грн", "Оціночна сума, EUR", "Метод закупівлі", "Тип ТД", "Опубліковано в OJEU",
    "План. публікація PROZORRO", "План. подання пропозицій", "План. підписання договору", "План. завершення договору", "Статус закупівлі", "Місцеве співфінансування", "Коментарі"
)
private val columnWidths = listOf(55, 75, 160, 90, 230, 340, 135, 110, 160, 70, 150, 170, 170, 180, 160, 250, 180, 150, 145, 145, 145, 145, 230, 155, 260)
private val procurementStatuses = listOf(
    "Не розпочато / Not Started",
    "Закупівля триває / Tender Ongoing",
    "Повідомлення про намір укласти договір / Contract award notice",
    "Договір укладено / Contract signed",
    "Відмінено / Cancelled",
    "Договір розірвано / Contract terminated"
)

private fun sameProcurementStatus(first: String?, second: String?): Boolean =
    !first.isNullOrBlank() && !second.isNullOrBlank() && LocalizationManager.procurementStatus(first).equals(LocalizationManager.procurementStatus(second), ignoreCase = true)

private fun String.toProzorroTenderUrl(): String =
    if (startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)) this
    else "https://prozorro.gov.ua/tender/$this"

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
        Modifier.widthIn(max = 760.dp).fillMaxWidth(),
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
