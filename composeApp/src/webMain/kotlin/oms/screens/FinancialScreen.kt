package oms.screens

import oms.components.rememberTablePageSize
import oms.components.rememberTableSetting
import oms.urp3.paymentSubprojectKey

import oms.data.WorkspaceBrand
import oms.components.PageArrow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import oms.model.localizedName
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import oms.data.ApiFinancialRecord
import oms.data.ApiFinancialProgress
import oms.data.ApiFinancialProgressItem
import oms.data.ApiPaymentMonitoring
import oms.data.ApiPaymentMonitoringItem
import oms.data.ApiPaymentMonitoringTransaction
import oms.data.ApiPaymentMonitoringRefresh
import oms.data.displayAmountCents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
import oms.components.canonicalUkraineRegion
import oms.components.localizedUkraineRegion
import oms.components.sectorLabel
import oms.components.SearchableOptionPicker
import oms.components.currentIsoDate
import oms.components.safeTextInput
import oms.components.WasmSafeOverlay
import oms.screens.dashboard.toMonthName
import kotlin.js.JsName
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@JsName("openFinancialImport")
external fun openFinancialImport(projectUuid: String, onComplete: (String) -> Unit)

@JsName("openPaymentMonitoringWorkbookUpload")
external fun openPaymentMonitoringWorkbookUpload(onComplete: (String, String) -> Unit)

@JsName("openGooglePaymentMonitoringRefresh")
external fun openGooglePaymentMonitoringRefresh(onComplete: (String, String) -> Unit)

@JsName("downloadFinancialExport")
external fun downloadFinancialExport(projectUuid: String)

private data class ProjectActRow(
    val projectUuid: String,
    val subprojectUuid: String?,
    val subprojectName: String,
    val subprojectCode: String?,
    val subprojectPartName: String?,
    val trancheNumber: Int,
    val region: String,
    val act: ApiFinancialRecord
)

private enum class FinancialSort { Type, Purpose, Tranche, Subproject, SubprojectCode, SubprojectPart, ActDate, Amount, Currency, Description, Author }
private enum class PaymentMonitoringSort { Code, Total, Eib, Local, PaidAndInProgress, Paid, Remaining, PlannedShare, FactualShare, Compliance }
private enum class PaymentMonitoringTransactionSort { Date, Code, Tranche, Uah, Eur }

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
fun FinancialScreen(
    canAccessFinancials: Boolean = true,
    canManageFinancials: Boolean = true,
    canUpdatePaymentMonitoring: Boolean = false,
    requestedSubprojectUuid: String? = null,
    requestedRegion: String? = null,
    requestedMonth: String? = null,
    requestedTranche: Int? = null,
    onRequestedSubprojectFilterConsumed: () -> Unit = {},
    onRequestedRegionFilterConsumed: () -> Unit = {},
    onRequestedMonthFilterConsumed: () -> Unit = {}
) {
    if (!canAccessFinancials) {
        FinancialAccessDenied()
        return
    }

    val useLedger = !WorkspaceBrand.legacySources
    var acts by remember { mutableStateOf<List<ProjectActRow>>(emptyList()) }
    var paymentMonitoring by remember { mutableStateOf<ApiPaymentMonitoring?>(null) }
    var paymentMonitoringLoading by remember { mutableStateOf(!useLedger) }
    var paymentMonitoringLoadFailed by remember { mutableStateOf(false) }
    var paymentMonitoringMessage by remember { mutableStateOf<String?>(null) }
    var paymentMonitoringMessageIsError by remember { mutableStateOf(false) }
    var displayCurrency by rememberTableSetting("financial.currency", "UAH") { it.also { currency -> require(currency in listOf("EUR", "UAH")) } }
    var displayRates by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var loadingRates by remember { mutableStateOf(false) }
    var rateRetry by remember { mutableStateOf(0) }
    LaunchedEffect(acts, displayCurrency, rateRetry) {
        val dates = acts.filter { row ->
            val currencies = if (useLedger) listOf("EUR", "UAH") else listOf(displayCurrency)
            currencies.any { row.act.displayAmountCents(it, displayRates[row.act.recordDate]) == null }
        }
            .map { it.act.recordDate }.distinct()
        loadingRates = dates.isNotEmpty()
        try {
            val gate = Semaphore(4)
            val fetched = coroutineScope {
                dates.map { date -> async {
                    gate.withPermit {
                        try { date to OmsApiClient.projectExchangeRate(date).uahPerEur.toDouble() }
                        catch (failure: Exception) {
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            null
                        }
                    }
                } }.awaitAll().filterNotNull().toMap()
            }
            displayRates = displayRates + fetched
        } finally { loadingRates = false }
    }
    fun displayCents(record: ApiFinancialRecord) = record.displayAmountCents(displayCurrency, displayRates[record.recordDate])
    val missingConversions = acts.count { displayCents(it.act) == null }
    var recordTypeFilter by rememberTableSetting<String?>("financial.scope.type", null) { it.takeIf(String::isNotEmpty) }
    var paymentPurposeFilter by rememberTableSetting<String?>("financial.scope.purpose", null) { it.takeIf(String::isNotEmpty) }
    var trancheFilter by rememberTableSetting<Int?>("financial.scope.tranche", null) { it.toIntOrNull() }
    var subprojectFilter by rememberTableSetting<String?>("financial.scope.subproject", null) { it.takeIf(String::isNotEmpty) }
    var regionFilter by rememberTableSetting<String?>("financial.scope.region", null) { it.takeIf(String::isNotEmpty) }
    var chartMonthFilter by rememberTableSetting<String?>("financial.scope.month", null) { it.takeIf(String::isNotEmpty) }
    var yearFilter by rememberTableSetting<Int?>("financial.scope.year", null) { it.toIntOrNull() }
    var sort by remember { mutableStateOf(FinancialSort.ActDate) }
    var ascending by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var financialProgressRetry by remember { mutableStateOf(0) }
    // Bind values and their load state to the exact query. Never show a previous
    // region's totals under the newly selected scope's caption, even for a frame.
    var financialProgress by remember(reloadKey, financialProgressRetry, regionFilter, trancheFilter, subprojectFilter, yearFilter) {
        mutableStateOf<ApiFinancialProgress?>(null)
    }
    var financialProgressLoading by remember(reloadKey, financialProgressRetry, regionFilter, trancheFilter, subprojectFilter, yearFilter) { mutableStateOf(true) }
    var financialProgressLoadFailed by remember(reloadKey, financialProgressRetry, regionFilter, trancheFilter, subprojectFilter, yearFilter) { mutableStateOf(false) }
    var paymentMonitoringRefreshConfirmationOpen by remember { mutableStateOf(false) }
    var paymentMonitoringRefreshJob by remember { mutableStateOf<ApiPaymentMonitoringRefresh?>(null) }
    var paymentMonitoringRefreshElapsedSeconds by remember { mutableStateOf(0) }
    var editAct by remember { mutableStateOf<ProjectActRow?>(null) }
    var addAct by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var legacyLoading by remember { mutableStateOf(false) }
    var legacyLoadFailed by remember { mutableStateOf(false) }
    oms.navigation.ReportRouteReadiness(loading, loadFailed)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageSize by rememberTablePageSize("financial")
    var currentPage by remember { mutableStateOf(0) }
    var monitoringCodeQuery by rememberTableSetting("financial.monitoring.search", "") { it }
    var filterResetRevision by remember { mutableStateOf(0) }
    val resetAllFilters: () -> Unit = {
        recordTypeFilter = null
        paymentPurposeFilter = null
        trancheFilter = null
        subprojectFilter = null
        regionFilter = null
        chartMonthFilter = null
        yearFilter = null
        monitoringCodeQuery = ""
        currentPage = 0
        filterResetRevision++
    }
    var tableTopInRootPx by remember { mutableStateOf(0f) }
    var paymentMonitoringTopInRootPx by remember { mutableStateOf(0f) }
    val deletion = oms.components.LocalDeleteConfirmation.current
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollState()
    val tableTopPaddingPx = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(paymentMonitoringRefreshJob?.id) {
        val id = paymentMonitoringRefreshJob?.id ?: return@LaunchedEffect
        val startedAt = kotlin.time.TimeSource.Monotonic.markNow()
        paymentMonitoringRefreshElapsedSeconds = 0
        while (paymentMonitoringRefreshJob?.status in setOf("RUNNING", "CANCELLING")) {
            delay(400)
            paymentMonitoringRefreshElapsedSeconds = startedAt.elapsedNow().inWholeSeconds.toInt()
            runCatching { OmsApiClient.paymentMonitoringRefresh(id) }
                .onSuccess { updated ->
                    paymentMonitoringRefreshJob = updated
                    when (updated.status) {
                        "COMPLETED" -> {
                            ProjectRepository.invalidate()
                            paymentMonitoringMessage = LocalizationManager.t("payment_monitoring_imported")
                            paymentMonitoringMessageIsError = false
                            reloadKey++
                        }
                        "FAILED" -> {
                            paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import")
                                .replace("{message}", updated.error.orEmpty())
                            paymentMonitoringMessageIsError = true
                        }
                        "CANCELLED" -> {
                            paymentMonitoringMessage = LocalizationManager.t("payment_monitoring_refresh_cancelled")
                            paymentMonitoringMessageIsError = false
                        }
                    }
                }
                .onFailure { failure ->
                    paymentMonitoringRefreshJob = paymentMonitoringRefreshJob?.copy(status = "FAILED", error = failure.message)
                    paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import")
                        .replace("{message}", failure.message.orEmpty())
                    paymentMonitoringMessageIsError = true
                }
        }
    }

    LaunchedEffect(requestedSubprojectUuid, requestedRegion, requestedMonth, requestedTranche) {
        val next = financialNavigationScope(PaymentMonitoringScope(trancheFilter, subprojectFilter, regionFilter, yearFilter, chartMonthFilter, paymentPurposeFilter),
            requestedSubprojectUuid, requestedRegion, requestedMonth, requestedTranche)
        trancheFilter = next.tranche; subprojectFilter = next.subproject; regionFilter = next.region
        yearFilter = next.year; chartMonthFilter = next.month; paymentPurposeFilter = next.purpose
        if (requestedSubprojectUuid != null) onRequestedSubprojectFilterConsumed()
        if (requestedRegion != null) onRequestedRegionFilterConsumed()
        if (requestedMonth != null) onRequestedMonthFilterConsumed()
    }

    LaunchedEffect(reloadKey) {
        loading = true; loadFailed = false
        try {
            // The source is keyed by subproject code. Resolve this context
            // independently of the hidden legacy ledger and its exchange rates.
            ProjectRepository.refresh(force = useLedger && reloadKey > 0)
            check(ProjectRepository.errorMessage == null) { "Could not load project context" }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            loadFailed = true
        } finally { loading = false }
    }
    // These filters intentionally scope both the register and financial
    // charts. Otherwise a user could select a tranche or subproject and still
    // see a progress denominator from every subproject in the programme.
    LaunchedEffect(reloadKey, financialProgressRetry, regionFilter, trancheFilter, subprojectFilter, yearFilter) {
        try {
            loadFinancialProgressSnapshot {
                OmsApiClient.financialProgress(regionFilter, trancheFilter, subprojectFilter, yearFilter)
            }.onSuccess { financialProgress = it }
                .onFailure { financialProgressLoadFailed = true }
        } finally {
            financialProgressLoading = false
        }
    }
    // Resolve the approved source independently without blocking navigation.
    // Retain the previous snapshot during a refresh, but report load failures.
    LaunchedEffect(reloadKey) {
        if (useLedger) return@LaunchedEffect
        paymentMonitoringLoading = true
        paymentMonitoringLoadFailed = false
        try {
            paymentMonitoring = OmsApiClient.paymentMonitoring()
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            paymentMonitoringLoadFailed = true
        } finally {
            paymentMonitoringLoading = false
        }
    }
    LaunchedEffect(reloadKey, paymentMonitoringLoading, paymentMonitoring != null, loading, loadFailed) {
        legacyLoading = false
        legacyLoadFailed = false
        if (!shouldLoadLegacyFinancialRecords(paymentMonitoring != null, paymentMonitoringLoading, !loading && !loadFailed)) {
            acts = emptyList()
            return@LaunchedEffect
        }
        legacyLoading = true
        try {
            val projectsById = ProjectRepository.projects.associateBy { it.id }
            acts = OmsApiClient.allFinancialRecords().mapNotNull { item ->
                projectsById[item.projectUuid]?.let { project ->
                    val ancestry = generateSequence(project) { current -> current.parentProjectUuid?.let(projectsById::get) }.toList().asReversed()
                    val subproject = ancestry.firstOrNull { it.projectType == "subproject" }
                    val subprojectPart = ancestry.firstOrNull { it.projectType == "subproject_part" }
                    ProjectActRow(project.id, subproject?.id, subproject?.name ?: project.name,
                        subproject?.siteNumber ?: project.siteNumber, subprojectPart?.name,
                        subproject?.trancheNumber ?: project.trancheNumber,
                        canonicalUkraineRegion(subproject?.region ?: project.region), item.record)
                }
            }.sortedByDescending { it.act.recordDate }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            legacyLoadFailed = true
        } finally {
            legacyLoading = false
        }
    }

    val regionScopedActs = acts.filter { regionFilter == null || it.region == regionFilter }
    val subprojectsByCode = remember(ProjectRepository.projects) {
        ProjectRepository.projects.filter { it.projectType == "subproject" }
            .associateBy { it.siteNumber.trim().uppercase() }
    }
    val subprojectsByUuid = remember(subprojectsByCode) { subprojectsByCode.values.associateBy { it.id } }
    val workbookChartRows = remember(paymentMonitoring?.transactions, subprojectsByCode) {
        paymentMonitoring?.transactions.orEmpty().mapNotNull { transaction ->
            val subprojectCode = paymentSubprojectKey(transaction.code)
            val project = subprojectsByCode[subprojectCode.uppercase()] ?: return@mapNotNull null
            ProjectActRow(
                projectUuid = project.id,
                subprojectUuid = project.id,
                subprojectName = project.name,
                subprojectCode = project.siteNumber,
                subprojectPartName = null,
                trancheNumber = project.trancheNumber,
                region = canonicalUkraineRegion(project.region),
                act = approvedPaymentChartRecord(transaction)
            )
        }
    }
    val subprojectFilterOptions = remember(subprojectsByCode) {
        subprojectsByCode.values.filter { it.siteNumber.isNotBlank() }
            .map { it.id to it.siteNumber }.sortedBy { (_, code) -> code }
    }
    val regionFilterOptions = acts.map { it.region }.filter(String::isNotBlank).distinct().sorted()
    val yearFilterOptions = acts.map { (it.act.paymentDate ?: it.act.recordDate).take(4).toIntOrNull() }
        .filterNotNull().distinct().sortedDescending()
    // A region deep link is context, not merely a hidden table filter. Keep it
    // visible in the page title and every financial chart caption.
    val selectedRegionLabel = regionFilter?.let(::localizedUkraineRegion)
    val selectedChartScopeLabel = buildList {
        selectedRegionLabel?.let(::add)
        trancheFilter?.let { add("${LocalizationManager.t("tranche")} ${it.financialTrancheCode()}") }
        yearFilter?.let { add(it.toString()) }
        subprojectFilter?.let { selected ->
            subprojectFilterOptions.firstOrNull { it.first == selected }?.second?.let(::add)
        }
    }.joinToString(" · ").ifBlank { null }
    val selectedSubprojectCode = subprojectFilter?.let { selected ->
        subprojectFilterOptions.firstOrNull { it.first == selected }?.second
    }
    val selectedMonthLabel = chartMonthFilter?.let { "${it.toMonthName()} ${it.take(4)}" }
    val selectedPaymentScopeLabel = listOfNotNull(selectedChartScopeLabel, selectedMonthLabel).joinToString(" · ").ifBlank { null }
    val visibleActs = acts.filter {
        (recordTypeFilter == null || it.act.recordType == recordTypeFilter) &&
            (paymentPurposeFilter == null || it.act.paymentPurpose == paymentPurposeFilter) &&
            (trancheFilter == null || it.trancheNumber.isFinancialTranche(trancheFilter!!)) &&
            (subprojectFilter == null || it.subprojectUuid == subprojectFilter) &&
            (regionFilter == null || it.region == regionFilter) &&
            (chartMonthFilter == null || (it.act.paymentDate ?: it.act.recordDate).take(7) == chartMonthFilter) &&
            (yearFilter == null || (it.act.paymentDate ?: it.act.recordDate).take(4).toIntOrNull() == yearFilter)
    }.sortedWith(compareBy<ProjectActRow> {
        when (sort) {
            FinancialSort.Type -> it.act.recordType
            FinancialSort.Purpose -> it.act.paymentPurpose
            FinancialSort.Tranche -> it.trancheNumber.toString()
            FinancialSort.Subproject -> it.subprojectName
            FinancialSort.SubprojectCode -> it.subprojectCode.orEmpty()
            FinancialSort.SubprojectPart -> it.subprojectPartName.orEmpty()
            FinancialSort.ActDate -> it.act.recordDate
            FinancialSort.Amount -> (displayCents(it.act) ?: -1L).toString().padStart(20, '0')
            FinancialSort.Currency -> displayCurrency
            FinancialSort.Description -> (it.act.description ?: it.act.milestone).orEmpty()
            FinancialSort.Author -> ""
        }
    }.let { if (ascending) it else it.reversed() })
    val pageCount = if (visibleActs.isEmpty() || pageSize == Int.MAX_VALUE) 1
    else (visibleActs.size + pageSize - 1) / pageSize
    // Filters change the data set just like on the Subprojects screen: start
    // from page one so the pagination status always describes visible rows.
    LaunchedEffect(recordTypeFilter, paymentPurposeFilter, trancheFilter, subprojectFilter, regionFilter, chartMonthFilter, yearFilter, pageSize) {
        currentPage = 0
    }
    LaunchedEffect(visibleActs.size, pageSize) { currentPage = currentPage.coerceIn(0, pageCount - 1) }
    val pageRows = if (pageSize == Int.MAX_VALUE) visibleActs else visibleActs.drop(currentPage * pageSize).take(pageSize)
    val financialPdfFilterSummary = buildList {
        recordTypeFilter?.let { add("${LocalizationManager.t("type")}: ${LocalizationManager.t("record_type_$it")}") }
        paymentPurposeFilter?.let { add("${LocalizationManager.t("payment_purpose")}: ${LocalizationManager.t("payment_purpose_$it")}") }
        trancheFilter?.let { add("${LocalizationManager.t("tranche")}: ${it.financialTrancheCode()}") }
        subprojectFilter?.let { selected -> subprojectFilterOptions.firstOrNull { it.first == selected }?.second?.let { add("${LocalizationManager.t("subproject_code")}: $it") } }
        regionFilter?.let { add("${LocalizationManager.t("region")}: ${localizedUkraineRegion(it)}") }
        chartMonthFilter?.let { add("${LocalizationManager.t("date")}: ${it.toMonthName()} ${it.take(4)}") }
        yearFilter?.let { add("${LocalizationManager.t("date")}: $it") }
    }.joinToString(" · ")
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
    fun selectChartMonth(month: String, purpose: String) {
        chartMonthFilter = month
        paymentPurposeFilter = purpose
        scope.launch {
            delay(16)
            // Monthly source charts belong to the read-only payment-monitoring
            // source, not to the retired editable financial-record registry.
            val tableTop = if (useLedger) tableTopInRootPx else paymentMonitoringTopInRootPx
            val target = (pageScrollState.value + tableTop - tableTopPaddingPx)
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
            .verticalScroll(pageScrollState).padding(oms.layout.workspacePadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        oms.components.PageHeading(
            listOfNotNull(LocalizationManager.t("financial_monitoring"), selectedRegionLabel).joinToString(" — "),
            Icons.Default.AccountBalance,
            iconText = "€"
        ) {
            oms.components.WorkspaceOutlinedButton(
                modifier = Modifier.semantics { testTag = "financial-reset-filters" },
                onClick = resetAllFilters
            ) { Text(LocalizationManager.t("reset_filters")) }
            if (!useLedger && canUpdatePaymentMonitoring) oms.components.WorkspaceOutlinedButton(
                onClick = { paymentMonitoringMessage = null; paymentMonitoringRefreshConfirmationOpen = true },
                enabled = paymentMonitoringRefreshJob?.status !in setOf("RUNNING", "CANCELLING")
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(LocalizationManager.t("payment_monitoring_update"))
            }
            if (useLedger && canManageFinancials) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    oms.components.WorkspaceOutlinedButton(onClick = { errorMessage = null; showTransferDialog = true }) {
                        Text(LocalizationManager.t("import_export_xlsx"))
                    }
                    oms.components.WorkspaceButton(onClick = { errorMessage = null; addAct = true }) {
                        Icon(Icons.Default.Add, null)
                        Text(LocalizationManager.t("add_record"))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchableOptionPicker(
                options = subprojectFilterOptions.map { it.first }, selected = subprojectFilter,
                label = LocalizationManager.t("subproject"), onSelect = { subprojectFilter = it },
                placeholder = LocalizationManager.t("all_financial_subprojects"),
                itemLabel = { id -> subprojectsByUuid[id]?.let { "${it.siteNumber} — ${it.localizedName()}" }.orEmpty() },
                modifier = Modifier.weight(1f).semantics { testTag = "financial-subproject-filter" }, enabled = !loading
            )
        }
        val activeFilterLabels = buildList {
            selectedRegionLabel?.let { add("${LocalizationManager.t("region")}: $it") }
            trancheFilter?.let { add("${LocalizationManager.t("tranche")}: ${it.financialTrancheCode()}") }
            subprojectFilter?.let {
                add("${LocalizationManager.t("subproject_code")}: ${selectedSubprojectCode ?: "…"}")
            }
            yearFilter?.let { add("${LocalizationManager.t("date")}: $it") }
            selectedMonthLabel?.let { add("${LocalizationManager.t("date")}: $it") }
            paymentPurposeFilter?.let { add("${LocalizationManager.t("payment_purpose")}: ${LocalizationManager.t("payment_purpose_$it")}") }
            if (useLedger) recordTypeFilter?.let {
                add("${LocalizationManager.t("type")}: ${LocalizationManager.t("record_type_$it")}")
            }
            if (!useLedger && monitoringCodeQuery.isNotBlank()) {
                add("${LocalizationManager.t("subproject_code")}: ${monitoringCodeQuery.trim()}")
            }
        }
        if (activeFilterLabels.isNotEmpty()) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeFilterLabels.forEach { label ->
                    oms.components.OmsBadge(label, MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (loading || legacyLoading) oms.components.ContentState(LocalizationManager.t("loading_records"), loading = true)
        if (loadFailed) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { reloadKey++ })
        if (legacyLoadFailed) oms.components.ContentState(LocalizationManager.t("load_records_error"), error = true, onRetry = { reloadKey++ })
        if (paymentMonitoringLoading) oms.components.ContentState(LocalizationManager.t("payment_monitoring_loading"), loading = true)
        if (paymentMonitoringLoadFailed) oms.components.ContentState(
            LocalizationManager.t("payment_monitoring_load_error"), error = true, onRetry = { reloadKey++ })
        if (loadingRates) oms.components.ContentState(LocalizationManager.t("financial_rates_loading"), loading = true)
        else if (missingConversions > 0) oms.components.ContentState(
            LocalizationManager.t("financial_rates_missing"), error = true, onRetry = { rateRetry++ })

        val ledgerReady = useLedger && !loading && !loadFailed && !legacyLoading && !legacyLoadFailed
        val scopedLedger = regionScopedActs.filter {
            (subprojectFilter == null || it.subprojectUuid == subprojectFilter) &&
                (trancheFilter == null || it.trancheNumber.isFinancialTranche(trancheFilter!!)) &&
                (yearFilter == null || (it.act.paymentDate ?: it.act.recordDate).take(4).toIntOrNull() == yearFilter) &&
                (chartMonthFilter == null || (it.act.paymentDate ?: it.act.recordDate).take(7) == chartMonthFilter)
        }
        val cash = scopedLedger.filter { it.act.recordType in setOf("payment", "advance") }
        fun ledgerCents(rows: List<ProjectActRow>, currency: String): Long? {
            if (!ledgerReady) return null
            var total = 0L
            for (row in rows) {
                val cents = row.act.displayAmountCents(currency, displayRates[row.act.recordDate]) ?: return null
                if ((cents > 0 && total > Long.MAX_VALUE - cents) || (cents < 0 && total < Long.MIN_VALUE - cents)) return null
                total += cents
            }
            return total
        }
        val paymentProjects = remember(subprojectsByCode) { subprojectsByCode.values.map { PaymentProjectContext(it.id, it.siteNumber, it.trancheNumber, it.region) } }
        val paymentScope = PaymentMonitoringScope(trancheFilter, subprojectFilter, regionFilter, yearFilter, chartMonthFilter, paymentPurposeFilter)
        val contextualPayments = remember(paymentMonitoring?.transactions, paymentProjects, paymentScope.copy(purpose = null)) {
            scopePaymentTransactions(paymentMonitoring?.transactions.orEmpty(), paymentProjects, paymentScope.copy(purpose = null))
        }
        val paymentSourceReady = !loading && !loadFailed && !paymentMonitoringLoading && !paymentMonitoringLoadFailed
        val totalCaption = listOfNotNull(LocalizationManager.t("completed_works_by_acts"), selectedPaymentScopeLabel,
            paymentPurposeFilter?.let { LocalizationManager.t("payment_purpose_$it") }).joinToString(" · ")
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
                // Keep the source dashboard total for the whole programme;
                // filtered views must use the source's dated transactions.
                val total = if (useLedger) ledgerCents(scopedLedger.filter {
                    it.act.recordType == "act" && it.act.paymentPurpose == "works"
                }, "UAH")?.div(100.0) else if (paymentSourceReady) scopedPaymentTotalUah(
                    paymentMonitoring?.transactions, paymentProjects, paymentScope, paymentMonitoring?.summary?.amountPaidTotalUah
                ) else null
                val amount = total
                    ?.formatUiAmount()?.plus(" ${LocalizationManager.currencyLabel("UAH")}") ?: "—"
                if (maxWidth >= 620.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(totalCaption, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(amount, style = MaterialTheme.typography.headlineMedium)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(totalCaption, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(amount, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }

        fun paidFor(purpose: String): Double? = if (useLedger) {
            ledgerCents(cash.filter { it.act.paymentPurpose == purpose }, "UAH")?.div(100.0)
        } else if (paymentSourceReady && paymentMonitoring != null) {
            paymentAmountTotalUah(contextualPayments.filter { sourcePaymentPurpose(it.code) == purpose })
        } else null
        PaymentSummaryCards(paidFor("works"), paidFor("technical_supervision"), paidFor("engineer_consultant"), selectedPaymentScopeLabel)

        if (financialProgressLoading) oms.components.ContentState(
            LocalizationManager.t("financial_progress_loading"), loading = true)
        if (financialProgressLoadFailed) oms.components.ContentState(
            LocalizationManager.t("financial_progress_load_error"), error = true,
            onRetry = { financialProgressRetry++ })

        val chartRows = financialChartSourceRows(paymentMonitoring?.let { workbookChartRows }, paymentMonitoringLoading, acts)
        val financialChartRecords = remember(chartRows, trancheFilter, subprojectFilter, regionFilter, yearFilter) {
            chartRows.asSequence().filter {
                (trancheFilter == null || it.trancheNumber.isFinancialTranche(trancheFilter!!)) &&
                    (subprojectFilter == null || it.subprojectUuid == subprojectFilter) &&
                    (regionFilter == null || it.region == regionFilter) &&
                    (yearFilter == null || (it.act.paymentDate ?: it.act.recordDate).take(4).toIntOrNull() == yearFilter)
            }.map { FinancialChartRecord(it.act, it.subprojectCode.orEmpty()) }.toList()
        }
        // Rate availability is chart-local: an incomplete purpose shows its
        // own warning/retry without hiding unrelated, complete financial charts.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 760.dp
            val chartDensity = LocalDensity.current
            var adjacentHeightPx by remember(maxWidth) { mutableStateOf(0) }
            oms.components.AdaptiveChartRow(
                first = {
                    RegionalInvestmentChart(
                        remember(paymentMonitoring?.items, paymentProjects, paymentScope.subproject, paymentScope.region, paymentScope.tranche) {
                            regionalInvestment(paymentMonitoring?.items.orEmpty(), paymentProjects, paymentScope)
                        },
                        paymentMonitoringLoading || loading,
                        paymentMonitoringLoadFailed || loadFailed,
                        onRetry = { reloadKey++ }, titleSuffix = selectedChartScopeLabel,
                        targetHeight = if (twoColumns && adjacentHeightPx > 0) with(chartDensity) { adjacentHeightPx.toDp() } else null
                    )
                },
                second = {
                    Column(Modifier.fillMaxWidth().onGloballyPositioned { adjacentHeightPx = it.size.height },
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MonthlyPaymentsChart(financialChartRecords, displayRates, selectedChartScopeLabel) { month -> selectChartMonth(month, "works") }
                        financialProgress?.let { progress ->
                            FinancialProgressCard("financial_progress_by_construction_type", progress.byConstructionType, selectedChartScopeLabel)
                        }
                    }
                }
            )
        }
        oms.components.AdaptiveChartRow(
            first = { MonthlyTechnicalSupervisionPaymentsChart(financialChartRecords, displayRates, selectedChartScopeLabel) { month -> selectChartMonth(month, "technical_supervision") } },
            second = { MonthlyEngineerConsultantPaymentsChart(financialChartRecords, displayRates, selectedChartScopeLabel) { month -> selectChartMonth(month, "engineer_consultant") } }
        )
        oms.components.AdaptiveChartRow(
            first = {
                financialProgress?.let { progress ->
                    FinancialProgressCard("financial_progress_by_sector", progress.bySector, selectedChartScopeLabel, compactInitially = true)
                }
            },
            second = {}
        )
        MonthlyEquipmentPaymentsChart(financialChartRecords, displayRates, selectedChartScopeLabel) { month -> selectChartMonth(month, "equipment") }
        // Keep the update action available even when the last source snapshot
        // cannot be read, so an administrator can repair it without leaving
        // Financial Monitoring.
        if (!useLedger) Box(Modifier.onGloballyPositioned { paymentMonitoringTopInRootPx = it.positionInRoot().y }) {
            PaymentMonitoringCard(
                snapshot = paymentMonitoring ?: ApiPaymentMonitoring(refreshedAt = ""),
                selectedMonth = chartMonthFilter,
                projects = paymentProjects,
                context = paymentScope,
                codeQuery = monitoringCodeQuery,
                onCodeQueryChange = { monitoringCodeQuery = it },
                resetRevision = filterResetRevision,
                message = paymentMonitoringMessage,
                messageIsError = paymentMonitoringMessageIsError
            )
        }

        if (useLedger) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 1000.dp) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(LocalizationManager.t("financial_records"), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    oms.components.WorkspaceOutlinedButton(
                        onClick = {
                            oms.data.openOmsDownload(
                                OmsApiClient.financialTablePdfExportUrl(
                                    language = if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN) "en" else "uk",
                                    currency = displayCurrency,
                                    recordType = recordTypeFilter,
                                    paymentPurpose = paymentPurposeFilter,
                                    tranche = trancheFilter,
                                    subprojectUuid = subprojectFilter,
                                    region = regionFilter,
                                    month = chartMonthFilter,
                                    year = yearFilter,
                                    sort = sort.name.replaceFirstChar { it.lowercase() },
                                    ascending = ascending,
                                    filterSummary = financialPdfFilterSummary
                                )
                            )
                        },
                        enabled = visibleActs.isNotEmpty()
                    ) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("export_pdf")) }
                    FinancialPagination(pageSize, currentPage, pageCount, visibleActs.size, { pageSize = it }) { currentPage = it.coerceIn(0, pageCount - 1) }
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(LocalizationManager.t("financial_records"), style = MaterialTheme.typography.titleLarge)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        oms.components.WorkspaceOutlinedButton(
                            onClick = {
                                oms.data.openOmsDownload(OmsApiClient.financialTablePdfExportUrl(
                                    if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN) "en" else "uk", displayCurrency,
                                    recordTypeFilter, paymentPurposeFilter, trancheFilter, subprojectFilter, regionFilter, chartMonthFilter, yearFilter,
                                    sort.name.replaceFirstChar { it.lowercase() }, ascending, financialPdfFilterSummary
                                ))
                            }, enabled = visibleActs.isNotEmpty()
                        ) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text(LocalizationManager.t("export_pdf")) }
                        Spacer(Modifier.width(8.dp))
                        FinancialPagination(pageSize, currentPage, pageCount, visibleActs.size, { pageSize = it }) { currentPage = it.coerceIn(0, pageCount - 1) }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                oms.components.CurrencySelector(displayCurrency) { displayCurrency = it }
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
                FilterDropdown(
                    label = LocalizationManager.t("subproject_code"),
                    options = subprojectFilterOptions.map { it.first },
                    selected = subprojectFilter,
                    onSelect = { subprojectFilter = it },
                    itemLabel = { uuid -> subprojectFilterOptions.firstOrNull { it.first == uuid }?.second.orEmpty() }
                )
                FilterDropdown(
                    label = LocalizationManager.t("region"),
                    options = regionFilterOptions,
                    selected = regionFilter,
                    onSelect = { regionFilter = it },
                    itemLabel = ::localizedUkraineRegion
                )
                FilterDropdown(
                    label = LocalizationManager.t("date"),
                    options = yearFilterOptions,
                    selected = yearFilter,
                    onSelect = { yearFilter = it },
                    itemLabel = Int::toString
                )
                chartMonthFilter?.let { month ->
                    AssistChip(
                        onClick = { chartMonthFilter = null },
                        label = { Text("${LocalizationManager.t("date")}: ${month.toMonthName()} ${month.take(4)}") }
                    )
                }
            }
            oms.components.ScrollableTable(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { tableTopInRootPx = it.positionInRoot().y },
                pageScrollState = pageScrollState,
                pinnedStartWidth = 160.dp,
                pinnedStartHeader = { FinancialFrozenCodeHeader(sort, ascending, ::selectSort) },
                pinnedStartContent = {
                    pageRows.forEach { row ->
                        Row(Modifier.width(160.dp).padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(row.subprojectCode ?: "—", Modifier.width(160.dp))
                        }
                        HorizontalDivider()
                    }
                },
                header = { FinancialTableHeader(sort, ascending, ::selectSort); HorizontalDivider() }
            ) {
                        if (ledgerReady && visibleActs.isEmpty()) Text(LocalizationManager.t(emptyRecordsMessage))
                        pageRows.forEach { row ->
                            Row(Modifier.width(1_681.dp).padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(row.subprojectCode ?: "—", Modifier.width(160.dp))
                                Text(LocalizationManager.t("record_type_${row.act.recordType}"), Modifier.width(95.dp))
                                Box(Modifier.width(210.dp)) { FinancialPaymentPurposeBadge(row.act.recordType, row.act.paymentPurpose) }
                                Text(row.trancheNumber.financialTrancheCode(), Modifier.width(95.dp))
                                ExpandableTableText(row.subprojectName, Modifier.width(200.dp))
                                ExpandableTableText(row.subprojectPartName ?: "—", Modifier.width(200.dp))
                                Text(row.act.recordDate.toOmsDate(), Modifier.width(105.dp))
                                Text(displayCents(row.act)?.let { (it / 100.0).toMoney("").trim() } ?: "—", Modifier.width(130.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text(displayCurrency, Modifier.width(65.dp))
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
    }
    if (addAct || editAct != null) { WasmSafeOverlay(onDismiss = { addAct = false; editAct = null }, errorMessage = errorMessage) {
            ActEditorDialog(editAct, ProjectRepository.projects, acts, {
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
    if (paymentMonitoringRefreshConfirmationOpen) { WasmSafeOverlay(onDismiss = { paymentMonitoringRefreshConfirmationOpen = false }) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(LocalizationManager.t("payment_monitoring_refresh_confirm_title"), style = MaterialTheme.typography.titleLarge)
                    if (WorkspaceBrand.legacySources) Text(LocalizationManager.t("payment_monitoring_refresh_confirm_message"), style = MaterialTheme.typography.bodyMedium)
                    oms.components.WrappingActions(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        oms.components.WorkspaceOutlinedButton(onClick = { paymentMonitoringRefreshConfirmationOpen = false }) { Text(LocalizationManager.t("cancel")) }
                        oms.components.WorkspaceOutlinedButton(onClick = {
                            paymentMonitoringRefreshConfirmationOpen = false
                            openPaymentMonitoringWorkbookUpload { uploadError, refreshJson ->
                                if (uploadError.isNotBlank()) {
                                    paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import").replace("{message}", uploadError)
                                    paymentMonitoringMessageIsError = true
                                } else {
                                    runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ApiPaymentMonitoringRefresh>(refreshJson) }
                                        .onSuccess { paymentMonitoringRefreshJob = it }
                                        .onFailure { error ->
                                            paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import").replace("{message}", error.message.orEmpty())
                                            paymentMonitoringMessageIsError = true
                                        }
                                }
                            }
                        }) { Text(LocalizationManager.t("payment_monitoring_refresh_manual")) }
                    }
                    if (WorkspaceBrand.legacySources) oms.components.WorkspaceButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        paymentMonitoringRefreshConfirmationOpen = false
                        openGooglePaymentMonitoringRefresh { connectionError, refreshJson ->
                            if (connectionError.isNotBlank()) {
                                paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import").replace("{message}", connectionError)
                                paymentMonitoringMessageIsError = true
                            } else {
                                runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<ApiPaymentMonitoringRefresh>(refreshJson) }
                                    .onSuccess { paymentMonitoringRefreshJob = it }
                                    .onFailure { error ->
                                        paymentMonitoringMessage = LocalizationManager.t("error_payment_monitoring_import").replace("{message}", error.message.orEmpty())
                                        paymentMonitoringMessageIsError = true
                                    }
                            }
                        }
                    }) { Text(LocalizationManager.t("payment_monitoring_refresh_direct")) }
                }
            }
        } }
    paymentMonitoringRefreshJob?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }?.let { job ->
        WasmSafeOverlay(onDismiss = {}) {
            Card(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(LocalizationManager.t("payment_monitoring_refresh_progress_title"), style = MaterialTheme.typography.titleLarge)
                    Text(paymentMonitoringRefreshPhaseLabel(job.phase), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { (job.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${job.progress}% · ${formatPaymentMonitoringRefreshElapsed(paymentMonitoringRefreshElapsedSeconds)}", style = MaterialTheme.typography.bodySmall)
                    oms.components.WorkspaceOutlinedButton(
                        enabled = job.status == "RUNNING",
                        onClick = { scope.launch { runCatching { OmsApiClient.cancelPaymentMonitoringRefresh(job.id) }.onSuccess { paymentMonitoringRefreshJob = it } } },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(LocalizationManager.t("payment_monitoring_refresh_cancel")) }
                }
            }
        }
    }
    }
}

private fun formatPaymentMonitoringRefreshElapsed(seconds: Int): String =
    "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"

private fun paymentMonitoringRefreshPhaseLabel(phase: String): String =
    LocalizationManager.t("payment_monitoring_refresh_phase_$phase")
        .takeUnless { it == "payment_monitoring_refresh_phase_$phase" }
        ?: phase

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
                oms.components.WorkspaceOutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                oms.components.WorkspaceOutlinedButton(onClick = { projectUuid?.let(onExport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("export_to_excel")) }
                oms.components.WorkspaceButton(onClick = { projectUuid?.let(onImport) }, enabled = projectUuid != null) { Text(LocalizationManager.t("import_xls")) }
            }
        }
    }
}

@Composable
private fun ActEditorDialog(
    existing: ProjectActRow?,
    projects: List<oms.model.Project>,
    knownFinancialRows: List<ProjectActRow>,
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
    var date by remember { mutableStateOf(existing?.act?.paymentDate ?: existing?.act?.recordDate ?: currentIsoDate()) }
    var description by remember { mutableStateOf(existing?.act?.description ?: existing?.act?.milestone ?: "") }
    var paymentPurpose by remember { mutableStateOf(existing?.act?.paymentPurpose ?: "works") }
    var basisActs by remember { mutableStateOf<List<ApiFinancialRecord>>(emptyList()) }
    var loadingBasisActs by remember { mutableStateOf(false) }
    LaunchedEffect(recordType, projectUuid, availableProjects, knownFinancialRows) {
        val selectedTargetUuid = projectUuid
        if (recordType !in setOf("payment", "advance") || selectedTargetUuid == null) {
            basisActs = emptyList()
            loadingBasisActs = false
            return@LaunchedEffect
        }
        loadingBasisActs = true
        // A payment for a subproject part can be based on an act recorded on
        // that part or directly on its parent subproject.  Read the register
        // already loaded for this screen first (instant UI); fall back to the
        // narrowly scoped API calls only when it has no matching act.
        val selectedTarget = availableProjects.firstOrNull { it.id == selectedTargetUuid }
        val basisTargetUuids = buildSet {
            add(selectedTargetUuid)
            selectedTarget?.takeIf { it.projectType.equals("subproject_part", true) }
                ?.parentProjectUuid?.let(::add)
        }
        val localActs = knownFinancialRows.asSequence()
            .filter { it.projectUuid in basisTargetUuids && it.act.recordType.equals("act", true) }
            .map { it.act }
            .toList()
        basisActs = if (localActs.isNotEmpty()) {
            localActs
        } else {
            basisTargetUuids.flatMap { targetUuid ->
                runCatching { OmsApiClient.financials(targetUuid).data }.getOrDefault(emptyList())
            }.filter { it.recordType.equals("act", true) }
        }.distinctBy { it.uuid }
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
            OutlinedTextField(reference, { reference = it.safeTextInput(500) }, label = { Text(LocalizationManager.t("reference_number")) }, modifier = Modifier.fillMaxWidth())
            oms.components.WrappingActions(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("invoice", "act", "payment", "advance").forEach { type -> oms.components.WorkspaceFilterChip(selected = recordType == type, onClick = { recordType = type }, label = { Text(LocalizationManager.t("record_type_$type")) }) }
            }
            if (recordType in setOf("payment", "advance")) {
                Text(LocalizationManager.t("payment_purpose"), style = MaterialTheme.typography.labelLarge)
                InlineOptionPicker(options = listOf("works", "equipment", "technical_supervision", "engineer_consultant"),
                    selected = paymentPurpose, prompt = LocalizationManager.t("payment_purpose"), onSelect = { paymentPurpose = it },
                    itemLabel = { LocalizationManager.t("payment_purpose_$it") })
            }
            if (recordType in setOf("payment", "advance") && basisActs.isNotEmpty()) {
                val selectedBasis = basisActs.firstOrNull { it.referenceNumber == description }
                SearchableOptionPicker(
                    options = basisActs,
                    selected = selectedBasis,
                    label = LocalizationManager.t("payment_basis"),
                    onSelect = { act ->
                        description = act.referenceNumber
                        amount = act.amount.toString()
                        currency = act.currency
                    },
                    itemLabel = { act -> "${LocalizationManager.t("record_type_act")} ${act.referenceNumber} · ${act.recordDate.toOmsDate()} · ${act.amount.toMoney(act.currency)}" }
                )
            } else if (recordType in setOf("payment", "advance")) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.safeTextInput(2_000) },
                    label = { Text(LocalizationManager.t("payment_basis")) },
                    placeholder = { Text(LocalizationManager.t("payment_basis_hint")) },
                    supportingText = if (loadingBasisActs) { { Text(LocalizationManager.t("loading_records")) } } else null,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
            OutlinedTextField(
                value = amount,
                onValueChange = { entered ->
                    val filtered = entered.take(32)
                        .filter { it.isDigit() || it == '.' || it == ',' }
                        .replace(',', '.')
                    amount = if (filtered.matches(Regex("\\d{0,13}(\\.\\d{0,2})?"))) filtered else amount
                },
                label = { Text("${LocalizationManager.t("amount")}, $currency") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(LocalizationManager.t("original_record_currency"), style = MaterialTheme.typography.labelLarge)
            oms.components.WrappingActions(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("EUR", "UAH").forEach { code ->
                    oms.components.WorkspaceFilterChip(
                        selected = currency == code,
                        onClick = { currency = code },
                        label = { Text(LocalizationManager.t("currency_${code.lowercase()}")) }
                    )
                }
            }
            OmsDateField(date, { date = it }, LocalizationManager.t("date"), Modifier.fillMaxWidth(), true)
            if (recordType !in setOf("payment", "advance")) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.safeTextInput(8_000) },
                    label = { Text(LocalizationManager.t("description")) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                oms.components.WorkspaceOutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("cancel")) }
                oms.components.WorkspaceButton(onClick = { onSave(projectUuid!!, oms.data.FinancialRecordRequest(recordType, reference, parsedAmount!!, currency, date, description = description.ifBlank { null }, milestone = null, paymentPurpose = paymentPurpose)) }, enabled = valid) { Text(LocalizationManager.t("save")) }
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
    Row(Modifier.width(1_681.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(LocalizationManager.t("subproject_code"), sort == FinancialSort.SubprojectCode, ascending, { onSort(FinancialSort.SubprojectCode) }, Modifier.width(160.dp))
        SortableTableHeader(LocalizationManager.t("type"), sort == FinancialSort.Type, ascending, { onSort(FinancialSort.Type) }, Modifier.width(95.dp))
        SortableTableHeader(LocalizationManager.t("payment_purpose"), sort == FinancialSort.Purpose, ascending, { onSort(FinancialSort.Purpose) }, Modifier.width(210.dp))
        SortableTableHeader(LocalizationManager.t("tranche"), sort == FinancialSort.Tranche, ascending, { onSort(FinancialSort.Tranche) }, Modifier.width(95.dp))
        SortableTableHeader(LocalizationManager.t("subproject"), sort == FinancialSort.Subproject, ascending, { onSort(FinancialSort.Subproject) }, Modifier.width(200.dp))
        SortableTableHeader(LocalizationManager.t("subproject_part"), sort == FinancialSort.SubprojectPart, ascending, { onSort(FinancialSort.SubprojectPart) }, Modifier.width(200.dp))
        SortableTableHeader(LocalizationManager.t("date"), sort == FinancialSort.ActDate, ascending, { onSort(FinancialSort.ActDate) }, Modifier.width(105.dp))
        SortableTableHeader(LocalizationManager.t("amount"), sort == FinancialSort.Amount, ascending, { onSort(FinancialSort.Amount) }, Modifier.width(130.dp), numeric = true)
        SortableTableHeader(LocalizationManager.t("currency_short"), sort == FinancialSort.Currency, ascending, { onSort(FinancialSort.Currency) }, Modifier.width(65.dp))
        SortableTableHeader(LocalizationManager.t("description"), sort == FinancialSort.Description, ascending, { onSort(FinancialSort.Description) }, Modifier.width(250.dp))
        SortableTableHeader(LocalizationManager.t("author"), sort == FinancialSort.Author, ascending, { onSort(FinancialSort.Author) }, Modifier.width(75.dp))
        Box(Modifier.width(96.dp).height(52.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(LocalizationManager.t("actions"), style = MaterialTheme.typography.labelMedium) }
    }
}

/** Mirrors the leading code cell only after horizontal movement in ScrollableTable. */
@Composable
private fun FinancialFrozenCodeHeader(sort: FinancialSort, ascending: Boolean, onSort: (FinancialSort) -> Unit) {
    Row(Modifier.width(160.dp).padding(vertical = 6.dp)) {
        SortableTableHeader(
            LocalizationManager.t("subproject_code"),
            sort == FinancialSort.SubprojectCode,
            ascending,
            { onSort(FinancialSort.SubprojectCode) },
            Modifier.width(160.dp)
        )
    }
    HorizontalDivider()
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
    oms.components.AdaptivePagination(sizeControls = {
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
    }, navigation = {
        oms.components.PageNavigation(
            currentPage = currentPage,
            pageCount = pageCount,
            summary = LocalizationManager.t("page_of")
                .replace("{page}", (if (total == 0) 0 else currentPage + 1).toString())
                .replace("{pages}", (if (total == 0) 0 else pageCount).toString())
                .replace("{total}", total.toString()),
            onPage = onPage
        )
    })
}

private fun Int.isFinancialTranche(filter: Int): Boolean = when (filter) {
    1 -> this == 1 || this == 8
    2 -> this == 2 || this == 9
    else -> this == filter
}

/** The database stores the tranche as a number; the register deliberately exposes only its A/B code. */
private fun Int.financialTrancheCode(): String = if (this == 2 || this == 9) "B" else "A"

/** Compact, source-backed payment totals shown above the read-only register. */
@Composable
private fun PaymentSummaryCards(works: Double?, supervision: Double?, consultant: Double?, scopeLabel: String? = null) {
    val entries = listOf(
        "payment_purpose_works" to works,
        "payment_purpose_technical_supervision" to supervision,
        "payment_purpose_engineer_consultant" to consultant
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { (labelKey, amount) ->
            Card(
                modifier = Modifier.widthIn(min = 220.dp).weight(1f, fill = true),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        listOfNotNull(LocalizationManager.t(labelKey), scopeLabel).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        amount?.let { "${it.formatUiAmount()} ${LocalizationManager.currencyLabel("UAH")}" } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialProgressCard(
    titleKey: String,
    items: List<ApiFinancialProgressItem>,
    titleSuffix: String? = null,
    compactInitially: Boolean = false,
    onCollapsed: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(true) }
    var showAllItems by remember { mutableStateOf(!compactInitially) }
    val sortedItems = oms.charts.sortChartCategories(
        items, oms.charts.ChartSortCriterion.Label, true,
        labelOf = { financialProgressLabel(it.label, titleKey) }, valueOf = { it.completionPct }
    )
    // The compact sector card must still give a meaningful overview. Four
    // sectors fit without making its paired row taller, while the remaining
    // items stay available through the existing expand control.
    val initialItemCount = if (titleKey == "financial_progress_by_sector") {
        minOf(4, sortedItems.size)
    } else {
        maxOf(1, (sortedItems.size + 5) / 6)
    }
    val visibleItems = if (compactInitially && !showAllItems) sortedItems.take(initialItemCount) else sortedItems
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    listOfNotNull(LocalizationManager.t(titleKey), titleSuffix).joinToString(" — "),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                TableActionIconButton(
                    tooltip = LocalizationManager.t(if (expanded) "collapse" else "expand"),
                    icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                ) {
                    val wasExpanded = expanded
                    expanded = !expanded
                    if (wasExpanded) onCollapsed()
                }
            }
            if (expanded && items.isEmpty()) {
                Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (expanded) visibleItems.forEach { item ->
                val label = financialProgressLabel(item.label, titleKey)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        Text(if (item.hasCompleteAmounts) "${item.completionPct.roundToInt()}%" else "—", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.hasCompleteAmounts) LinearProgressIndicator(
                        progress = { (item.completionPct / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (titleKey.contains("sector")) oms.components.priorityDirectionColor(item.label) else oms.components.constructionTypeColor(item.label)
                    )
                    Text(
                        "${if (item.hasCompleteAmounts) item.completedAmountUah.formatUiAmount() else "—"} ${LocalizationManager.currencyLabel("UAH")} / ${item.contractAmountUah.formatUiAmount()} ${LocalizationManager.currencyLabel("UAH")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!item.hasCompleteAmounts) Text(LocalizationManager.t("financial_progress_incomplete_amount"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (expanded && compactInitially && sortedItems.size > initialItemCount) {
                oms.components.WorkspaceTextButton(onClick = { showAllItems = !showAllItems }) {
                    Text(LocalizationManager.t(if (showAllItems) "collapse" else "expand"))
                }
            }
        }
    }
}

private fun financialProgressLabel(label: String, titleKey: String): String = when (titleKey) {
    "financial_progress_by_region" -> label.takeUnless { it.equals("Unspecified", true) }
        ?.let(::localizedUkraineRegion) ?: LocalizationManager.t("construction_unspecified")
    "financial_progress_by_sector" -> label.takeUnless { it.equals("Unspecified", true) }
        ?.sectorLabel() ?: LocalizationManager.t("construction_unspecified")
    else -> if (label.equals("Unspecified", true)) LocalizationManager.t("construction_unspecified")
        else LocalizationManager.t("construction_$label").takeUnless { it == "construction_$label" } ?: label
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMonitoringCard(
    snapshot: ApiPaymentMonitoring,
    selectedMonth: String?,
    projects: List<PaymentProjectContext>,
    context: PaymentMonitoringScope,
    codeQuery: String,
    onCodeQueryChange: (String) -> Unit,
    resetRevision: Int,
    message: String?,
    messageIsError: Boolean
) {
    // This is an independent source table, not an optional chart. Keep it
    // visible on opening Financial Monitoring so users can immediately see
    // the UR III subproject payment-monitoring data above financial records.
    var expanded by remember { mutableStateOf(true) }
    var page by remember(snapshot.items, resetRevision) { mutableStateOf(0) }
    var sort by rememberTableSetting("financial.monitoring.sort", PaymentMonitoringSort.Code) { PaymentMonitoringSort.valueOf(it) }
    var ascending by rememberTableSetting("financial.monitoring.ascending", true) { it.toBooleanStrict() }
    fun selectSort(column: PaymentMonitoringSort) {
        if (sort == column) ascending = !ascending else { sort = column; ascending = true }
        page = 0
    }
    val normalizedQuery = codeQuery.trim().lowercase()
    val scopedItems = remember(snapshot.items, projects, context.tranche, context.subproject, context.region) {
        scopePaymentItems(snapshot.items, projects, context)
    }
    val filteredRows = remember(scopedItems, normalizedQuery) {
        scopedItems.filter { row ->
            normalizedQuery.isBlank() || row.code.lowercase().contains(normalizedQuery)
        }
    }
    val rows = remember(filteredRows, sort, ascending) {
        filteredRows.sortedWith { left, right -> comparePaymentMonitoringRows(left, right, sort, ascending) }
    }
    val totals = remember(filteredRows) { paymentMonitoringTotals(filteredRows) }
    LaunchedEffect(normalizedQuery, context) { page = 0 }
    val pageSize = 20
    val pageCount = maxOf(1, (rows.size + pageSize - 1) / pageSize)
    val visibleRows = rows.drop(page.coerceIn(0, pageCount - 1) * pageSize).take(pageSize)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                oms.components.ExpandableTableText(paymentMonitoringTitle(context, projects, codeQuery),
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 3)
                TableActionIconButton(
                    tooltip = LocalizationManager.t(if (expanded) "collapse" else "expand"),
                    icon = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                ) { expanded = !expanded }
            }
            FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                oms.components.WorkspaceOutlinedButton(onClick = {
                    oms.data.openOmsDownload(
                        OmsApiClient.paymentMonitoringPdfExportUrl(
                            if (LocalizationManager.currentLanguage == oms.localization.Language.EN) "en" else "uk",
                            code = codeQuery.trim(), sort = sort.name, ascending = ascending,
                            selectedCodes = scopedItems.map { it.code },
                            month = context.month, year = context.year, purpose = context.purpose
                        )
                    )
                }, enabled = rows.isNotEmpty()) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(6.dp))
                    Text(LocalizationManager.t("export_pdf"))
                }
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                PaymentMonitoringCodeSearch(
                    codeQuery = codeQuery,
                    onCodeQueryChange = { onCodeQueryChange(it.safeTextInput(100)) },
                    filteredCount = rows.size,
                    totalCount = scopedItems.size
                )
                if (rows.isEmpty()) {
                    Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    oms.components.ScrollableTable(
                        modifier = Modifier.fillMaxWidth(),
                        pinnedStartWidth = 130.dp,
                        pinnedStartHeader = { PaymentMonitoringHeaderCell("subproject_code", 130.dp, PaymentMonitoringSort.Code, sort, ascending, ::selectSort) },
                        pinnedStartContent = {
                            visibleRows.forEach { row ->
                                Row(Modifier.width(130.dp).padding(vertical = 4.dp)) { PaymentMonitoringCell(row.code, 130.dp) }
                                HorizontalDivider()
                            }
                            Row(Modifier.width(130.dp).height(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
                                verticalAlignment = Alignment.CenterVertically) { PaymentMonitoringTotalLabel(130.dp) }
                            HorizontalDivider()
                        },
                        header = { PaymentMonitoringHeader(sort, ascending, ::selectSort) }
                    ) {
                        visibleRows.forEach { row ->
                            PaymentMonitoringRow(row)
                            HorizontalDivider()
                        }
                        PaymentMonitoringRow(totals, heading = true)
                        HorizontalDivider()
                    }
                    oms.components.PageNavigation(currentPage = page.coerceIn(0, pageCount - 1), pageCount = pageCount,
                        summary =
                            LocalizationManager.t("page_of")
                                .replace("{page}", (page.coerceIn(0, pageCount - 1) + 1).toString())
                                .replace("{pages}", pageCount.toString())
                                .replace("{total}", rows.size.toString()),
                        onPage = { page = it.coerceIn(0, pageCount - 1) })
                }
                PaymentMonitoringTransactionTable(scopePaymentTransactions(snapshot.transactions, projects, context), selectedMonth, codeQuery,
                    title = paymentMonitoringTitle(context.copy(month = selectedMonth ?: context.month), projects, codeQuery, includePeriod = true),
                    resetRevision = resetRevision)
            }
        }
    }
}

/** Cumulative indicators have no period/purpose grain; only transaction captions include those cuts. */
private fun paymentMonitoringTitle(
    context: PaymentMonitoringScope,
    projects: List<PaymentProjectContext>,
    codeQuery: String,
    includePeriod: Boolean = false
): String = buildList {
    add(LocalizationManager.t(if (includePeriod) "payment_register_title" else "payment_monitoring_title"))
    context.region?.let { add(localizedUkraineRegion(it)) }
    context.tranche?.let { add("${LocalizationManager.t("tranche")} ${it.financialTrancheCode()}") }
    val projectCode = projects.firstOrNull { it.uuid == context.subproject }?.code
    projectCode?.let(::add)
    codeQuery.trim().takeIf { it.isNotEmpty() && !it.equals(projectCode, ignoreCase = true) }
        ?.let { add("${LocalizationManager.t("subproject_code")}: $it") }
    if (includePeriod) {
        if (context.month != null) add("${context.month.toMonthName()} ${context.month.take(4)}")
        else context.year?.let { add(it.toString()) }
        context.purpose?.let { add(LocalizationManager.t("payment_purpose_$it")) }
    }
}.joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMonitoringCodeSearch(
    codeQuery: String,
    onCodeQueryChange: (String) -> Unit,
    filteredCount: Int,
    totalCount: Int
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        oms.components.WorkspaceSearchField(
            value = codeQuery,
            onValueChange = onCodeQueryChange,
            label = LocalizationManager.t("subproject_code"),
            modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth()
        )
        Text(
            LocalizationManager.t("payment_monitoring_filtered_count")
                .replace("{shown}", filteredCount.toString()).replace("{total}", totalCount.toString()),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/** Nulls consistently remain at the end in both ascending and descending views. */
private fun compareOptionalNumber(left: Double?, right: Double?, ascending: Boolean): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right).let { if (ascending) it else -it }
}

private fun compareOptionalInt(left: Int?, right: Int?, ascending: Boolean): Int = when {
    left == null && right == null -> 0
    left == null -> 1
    right == null -> -1
    else -> left.compareTo(right).let { if (ascending) it else -it }
}

private fun compareText(left: String, right: String, ascending: Boolean): Int =
    left.lowercase().compareTo(right.lowercase()).let { if (ascending) it else -it }

private fun compareOptionalText(left: String?, right: String?, ascending: Boolean): Int = when {
    left.isNullOrBlank() && right.isNullOrBlank() -> 0
    left.isNullOrBlank() -> 1
    right.isNullOrBlank() -> -1
    else -> compareText(left, right, ascending)
}

private fun comparePaymentMonitoringRows(
    left: ApiPaymentMonitoringItem,
    right: ApiPaymentMonitoringItem,
    sort: PaymentMonitoringSort,
    ascending: Boolean
): Int = when (sort) {
    PaymentMonitoringSort.Code -> compareText(left.code, right.code, ascending)
    PaymentMonitoringSort.Total -> compareOptionalNumber(left.totalInvestmentUah, right.totalInvestmentUah, ascending)
    PaymentMonitoringSort.Eib -> compareOptionalNumber(left.eibInvestmentUah, right.eibInvestmentUah, ascending)
    PaymentMonitoringSort.Local -> compareOptionalNumber(left.localCoFinancingUah, right.localCoFinancingUah, ascending)
    PaymentMonitoringSort.PaidAndInProgress -> compareOptionalNumber(left.paidAndInProgressUah, right.paidAndInProgressUah, ascending)
    PaymentMonitoringSort.Paid -> compareOptionalNumber(left.paidUah, right.paidUah, ascending)
    PaymentMonitoringSort.Remaining -> compareOptionalNumber(left.remainingUah, right.remainingUah, ascending)
    PaymentMonitoringSort.PlannedShare -> compareOptionalNumber(left.plannedEibSharePct, right.plannedEibSharePct, ascending)
    PaymentMonitoringSort.FactualShare -> compareOptionalNumber(left.factualEibSharePct, right.factualEibSharePct, ascending)
    PaymentMonitoringSort.Compliance -> compareOptionalText(left.eibShareCompliance, right.eibShareCompliance, ascending)
}

private fun comparePaymentTransactions(
    left: ApiPaymentMonitoringTransaction,
    right: ApiPaymentMonitoringTransaction,
    sort: PaymentMonitoringTransactionSort,
    ascending: Boolean
): Int = when (sort) {
    PaymentMonitoringTransactionSort.Date -> compareText(left.paymentDate, right.paymentDate, ascending)
    PaymentMonitoringTransactionSort.Code -> compareText(left.code, right.code, ascending)
    PaymentMonitoringTransactionSort.Tranche -> compareOptionalInt(left.batchId, right.batchId, ascending)
    PaymentMonitoringTransactionSort.Uah -> compareOptionalNumber(left.amountUah, right.amountUah, ascending)
    PaymentMonitoringTransactionSort.Eur -> compareOptionalNumber(left.amountEur, right.amountEur, ascending)
}

/**
 * The source worksheet contains the auditable transactions behind the monthly
 * chart.  It is shown below the cumulative register and follows a month click
 * from the Dashboard without converting those rows into editable documents.
 */
@Composable
private fun PaymentMonitoringTransactionTable(
    transactions: List<ApiPaymentMonitoringTransaction>,
    selectedMonth: String?,
    codeQuery: String,
    title: String,
    resetRevision: Int
) {
    var sort by rememberTableSetting("financial.transactions.sort", PaymentMonitoringTransactionSort.Date) { PaymentMonitoringTransactionSort.valueOf(it) }
    var ascending by rememberTableSetting("financial.transactions.ascending", true) { it.toBooleanStrict() }
    var page by remember(transactions, selectedMonth, resetRevision) { mutableStateOf(0) }
    fun selectSort(column: PaymentMonitoringTransactionSort) {
        if (sort == column) ascending = !ascending else { sort = column; ascending = true }
        page = 0
    }
    val normalizedQuery = codeQuery.trim().lowercase()
    val scopedTransactions = remember(transactions, selectedMonth) {
        transactions.filter { row -> selectedMonth == null || row.paymentDate.startsWith(selectedMonth) }
    }
    val filteredRows = remember(scopedTransactions, normalizedQuery) {
        scopedTransactions.filter { row ->
            normalizedQuery.isBlank() || row.code.lowercase().contains(normalizedQuery)
        }
    }
    val rows = remember(filteredRows, sort, ascending) {
        filteredRows.sortedWith { left, right -> comparePaymentTransactions(left, right, sort, ascending) }
    }
    val totals = remember(filteredRows) { paymentTransactionTotals(filteredRows) }
    LaunchedEffect(normalizedQuery, selectedMonth) { page = 0 }
    if (transactions.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.titleSmall
    )
    if (rows.isEmpty()) {
        Text(LocalizationManager.t("no_chart_data"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val pageSize = 20
    val pageCount = maxOf(1, (rows.size + pageSize - 1) / pageSize)
    val visibleRows = rows.drop(page.coerceIn(0, pageCount - 1) * pageSize).take(pageSize)
    oms.components.ScrollableTable(
        modifier = Modifier.fillMaxWidth(),
        header = {
            Row(Modifier.width(600.dp)) {
                PaymentSourceHeader(LocalizationManager.t("date"), 110.dp, PaymentMonitoringTransactionSort.Date, sort, ascending, ::selectSort)
                PaymentSourceHeader(LocalizationManager.t("subproject_code"), 160.dp, PaymentMonitoringTransactionSort.Code, sort, ascending, ::selectSort)
                PaymentSourceHeader(LocalizationManager.t("tranche"), 90.dp, PaymentMonitoringTransactionSort.Tranche, sort, ascending, ::selectSort)
                PaymentSourceHeader(LocalizationManager.currencyLabel("UAH"), 130.dp, PaymentMonitoringTransactionSort.Uah, sort, ascending, ::selectSort, true)
                PaymentSourceHeader("EUR", 110.dp, PaymentMonitoringTransactionSort.Eur, sort, ascending, ::selectSort, true)
            }
        }
    ) {
        visibleRows.forEach { row ->
            Row(Modifier.width(600.dp).padding(vertical = 4.dp)) {
                PaymentMonitoringCell(row.paymentDate, 110.dp)
                PaymentMonitoringCell(row.code, 160.dp)
                PaymentMonitoringCell(row.batchId?.let { if (it == 8) "A" else "B" } ?: "—", 90.dp)
                PaymentMonitoringCell(row.amountUah.asUahTableValue(), 130.dp)
                PaymentMonitoringCell(row.amountEur?.formatUiAmount() ?: "—", 110.dp)
            }
            HorizontalDivider()
        }
        Row(Modifier.width(600.dp).height(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
            verticalAlignment = Alignment.CenterVertically) {
            PaymentMonitoringTotalLabel(110.dp)
            PaymentMonitoringCell("—", 160.dp, heading = true)
            PaymentMonitoringCell("—", 90.dp, heading = true)
            PaymentMonitoringCell(totals.amountUah.asUahTableValue(), 130.dp, heading = true)
            PaymentMonitoringCell(totals.amountEur.asUahTableValue(), 110.dp, heading = true)
        }
        HorizontalDivider()
    }
    oms.components.PageNavigation(currentPage = page.coerceIn(0, pageCount - 1), pageCount = pageCount,
                        summary = LocalizationManager.t("page_of").replace("{page}", (page.coerceIn(0, pageCount - 1) + 1).toString())
            .replace("{pages}", pageCount.toString()).replace("{total}", rows.size.toString()),
                        onPage = { page = it.coerceIn(0, pageCount - 1) })
}

@Composable
private fun PaymentSourceHeader(
    title: String,
    width: androidx.compose.ui.unit.Dp,
    column: PaymentMonitoringTransactionSort,
    sort: PaymentMonitoringTransactionSort,
    ascending: Boolean,
    onSort: (PaymentMonitoringTransactionSort) -> Unit,
    numeric: Boolean = false
) = SortableTableHeader(title, sort == column, ascending, { onSort(column) }, Modifier.width(width), numeric = numeric)

@Composable
private fun PaymentMonitoringHeader(
    sort: PaymentMonitoringSort,
    ascending: Boolean,
    selectSort: (PaymentMonitoringSort) -> Unit
) {
    Row(Modifier.width(1_565.dp)) {
        PaymentMonitoringHeaderCell("subproject_code", 130.dp, PaymentMonitoringSort.Code, sort, ascending, selectSort)
        PaymentMonitoringHeaderCell("payment_monitoring_total_cost", 165.dp, PaymentMonitoringSort.Total, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_eib_cost", 180.dp, PaymentMonitoringSort.Eib, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_local_cost", 170.dp, PaymentMonitoringSort.Local, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_paid_progress", 190.dp, PaymentMonitoringSort.PaidAndInProgress, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_paid", 150.dp, PaymentMonitoringSort.Paid, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("remaining", 160.dp, PaymentMonitoringSort.Remaining, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_eib_planned", 130.dp, PaymentMonitoringSort.PlannedShare, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_eib_factual", 130.dp, PaymentMonitoringSort.FactualShare, sort, ascending, selectSort, true)
        PaymentMonitoringHeaderCell("payment_monitoring_eib_compliance", 160.dp, PaymentMonitoringSort.Compliance, sort, ascending, selectSort)
    }
}

@Composable
private fun PaymentMonitoringRow(row: oms.data.ApiPaymentMonitoringItem, heading: Boolean = false) {
    Row(Modifier.width(1_565.dp).then(if (heading) Modifier.height(44.dp).background(MaterialTheme.colorScheme.surfaceContainerLow)
        else Modifier.padding(vertical = 4.dp)), verticalAlignment = if (heading) Alignment.CenterVertically else Alignment.Top) {
        if (heading) PaymentMonitoringTotalLabel(130.dp) else PaymentMonitoringCell(row.code, 130.dp)
        PaymentMonitoringCell(row.totalInvestmentUah.asUahTableValue(), 165.dp, heading)
        PaymentMonitoringCell(row.eibInvestmentUah.asUahTableValue(), 180.dp, heading)
        PaymentMonitoringCell(row.localCoFinancingUah.asUahTableValue(), 170.dp, heading)
        PaymentMonitoringCell(row.paidAndInProgressUah.asUahTableValue(), 190.dp, heading)
        PaymentMonitoringCell(row.paidUah.asUahTableValue(), 150.dp, heading)
        PaymentMonitoringCell(row.remainingUah.asUahTableValue(), 160.dp, heading)
        PaymentMonitoringCell(row.plannedEibSharePct.asPercent(), 130.dp, heading)
        PaymentMonitoringCell(row.factualEibSharePct.asPercent(), 130.dp, heading)
        PaymentMonitoringCell(row.eibShareCompliance.asReadableComplianceStatus(), 160.dp, heading)
    }
}

@Composable
private fun PaymentMonitoringHeaderCell(
    labelKey: String,
    width: androidx.compose.ui.unit.Dp,
    column: PaymentMonitoringSort,
    sort: PaymentMonitoringSort,
    ascending: Boolean,
    selectSort: (PaymentMonitoringSort) -> Unit,
    numeric: Boolean = false
) {
    SortableTableHeader(
        title = LocalizationManager.t(labelKey),
        selected = sort == column,
        ascending = ascending,
        onClick = { selectSort(column) },
        modifier = Modifier.width(width),
        numeric = numeric,
        // Payment-monitoring captions are intentionally explicit.  Give the
        // header an extra line rather than truncating their meaning.
        height = 96.dp,
        maxLines = 5
    )
}

@Composable
private fun PaymentMonitoringTotalLabel(width: androidx.compose.ui.unit.Dp) {
    oms.components.OmsTooltipBox(tooltip = { Text(LocalizationManager.t("table_totals_hint")) }) {
        PaymentMonitoringCell(LocalizationManager.t("table_total"), width, heading = true)
    }
}

@Composable
private fun PaymentMonitoringCell(value: String, width: androidx.compose.ui.unit.Dp, heading: Boolean = false) {
    Text(
        value,
        modifier = Modifier.width(width).padding(horizontal = 6.dp),
        style = if (heading) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
        fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = if (heading) 3 else 2
    )
}

/** The column header already identifies these values as UAH. */
private fun Double?.asUahTableValue(): String = this?.let(Double::formatUiAmount) ?: "—"
private fun Double?.asPercent(): String = this?.let { "${it.formatUiAmount()}%" } ?: "—"

/** Imported workbook statuses may start with emoji that are not supported by every browser font. */
private fun String?.asReadableComplianceStatus(): String = this
    ?.replace("✓", "")
    ?.replace("✅", "")
    ?.replace("🔴", "")
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: "—"

@Composable
private fun FinancialPaymentPurposeBadge(recordType: String, purpose: String) {
    if (recordType !in setOf("payment", "advance")) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val labelKey = when (purpose) {
        "equipment" -> "payment_purpose_equipment"
        "technical_supervision" -> "payment_purpose_technical_supervision"
        "engineer_consultant" -> "payment_purpose_engineer_consultant"
        else -> "payment_purpose_works"
    }
    oms.components.OmsBadge(LocalizationManager.t(labelKey), oms.theme.paymentPurposeColor(purpose))
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
