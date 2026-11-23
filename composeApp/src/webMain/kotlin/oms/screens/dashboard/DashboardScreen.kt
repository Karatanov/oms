package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import kotlinx.browser.localStorage
import kotlinx.browser.sessionStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import oms.components.*
import oms.data.*
import oms.localization.LocalizationManager
import oms.screens.*
import oms.charts.BarChartOrientation
import kotlin.math.roundToLong

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun DashboardScreen(
    onOpenProject: (oms.model.Project) -> Unit = {},
    onOpenFinancialByMonth: (String, Int?) -> Unit = { _, _ -> },
    onOpenProjectsByRegion: (String, Int?) -> Unit = { _, _ -> },
    onOpenFinancialBySubproject: (String, Int?) -> Unit = { _, _ -> },
    onOpenProcurementsByStatus: (String, Int?) -> Unit = { _, _ -> },
    onOpenSubprojects: (Int?) -> Unit = {},
    onOpenMap: (Int?) -> Unit = {},
    onOpenFinancial: (Int?) -> Unit = {},
    onOpenInspectionPreview: (String) -> Unit = {}
) {
    var dashboard by remember { mutableStateOf<ApiDashboardOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    // The Dashboard overview aggregates several monitoring sources and can be
    // slow on a remote database.  It must never hold the whole workspace
    // hostage: make the shell, sidebar and navigation interactive first, then
    // render the charts' own updating state until the request completes.
    oms.navigation.ReportRouteReadiness(loading = false)
    var reload by remember { mutableStateOf(0) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    var latestPhotosLoading by remember { mutableStateOf(false) }
    var latestPhotosFailed by remember { mutableStateOf(false) }
    var photosReload by remember { mutableStateOf(0) }
    var chartOrientation by remember {
        val savedOrientation = runCatching { localStorage.getItem("oms.dashboard.chartOrientation") }.getOrNull()
        mutableStateOf(
            BarChartOrientation.values().firstOrNull { it.name == savedOrientation }
                ?: BarChartOrientation.Vertical
        )
    }
    var trancheNumber by remember {
        val savedTranche = runCatching { sessionStorage.getItem("oms.dashboard.tranche") }.getOrNull()
        mutableStateOf(savedTranche?.toIntOrNull()?.takeIf { it == 1 || it == 2 })
    }
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload, trancheNumber) {
        loading = true; error = false
        // Yield a frame first: the authenticated workspace and its navigation
        // become interactive before the remote overview begins loading.
        yield()
        // Production runs on an always-on VPS. Retrying a slow dashboard request
        // serially can multiply a backend/database delay and make login appear frozen.
        // Fail once and let the user retry explicitly instead.
        try {
            // The compact overview contains every displayed dashboard chart.
            // One request keeps completion, payments and KPI cards on a single
            // imported-data snapshot, then the photo preview can start as
            // soon as its recent-inspections list arrives.
            dashboard = OmsApiClient.dashboardOverview(trancheNumber)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = true
        } finally {
            loading = false
        }
    }
    // The overview deliberately returns only a compact recent-inspections list.
    // Keep navigation local to this list so browsing reports never re-fetches the
    // complete dashboard or loses the currently selected dashboard filters.
    val recentInspections = dashboard?.recentInspections
        .orEmpty()
        .sortedByDescending { it.inspectionDate }
    var selectedInspectionUuid by remember(dashboard?.recentInspections) {
        mutableStateOf<String?>(null)
    }
    val selectedInspectionIndex = recentInspections.indexOfFirst { it.uuid == selectedInspectionUuid }
        .takeIf { it >= 0 }
        ?: 0
    val selectedInspection = recentInspections.getOrNull(selectedInspectionIndex)
    LaunchedEffect(selectedInspection?.uuid, photosReload) {
        latestPhotos = null
        latestPhotosFailed = false
        latestPhotosLoading = selectedInspection != null
        try {
            latestPhotos = selectedInspection?.let {
                OmsApiClient.inspectionPhotos(it.uuid)
            } ?: emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            latestPhotosFailed = true
        } finally {
            latestPhotosLoading = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize().onKeyEvent { event ->
            val viewport = scrollState.layoutInfo
            val pageStep = ((viewport.viewportEndOffset - viewport.viewportStartOffset) * .8f).coerceAtLeast(1f)
            if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) false else when (event.key) {
                Key.PageUp -> { scope.launch { scrollState.animateScrollBy(-pageStep) }; true }
                Key.PageDown -> { scope.launch { scrollState.animateScrollBy(pageStep) }; true }
                Key.MoveHome -> { scope.launch { scrollState.animateScrollToItem(0) }; true }
                Key.MoveEnd -> { scope.launch { scrollState.animateScrollToItem((scrollState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }; true }
                else -> false
            }
        }.focusable(),
            contentPadding = oms.layout.workspacePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PageHeading(LocalizationManager.t("dashboard"), Icons.Default.Dashboard,
                    LocalizationManager.t("portfolio_overview"))
            }
            item {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    TrancheSelector(trancheNumber, onSelect = { option ->
                        if (trancheNumber != option) {
                            loading = true
                            error = false
                            trancheNumber = option
                            runCatching { sessionStorage.setItem("oms.dashboard.tranche", option?.toString() ?: "all") }
                        }
                    })
                    ChartOrientationPicker(chartOrientation) { orientation ->
                        chartOrientation = orientation
                        // Storage may be disabled by the browser; switching still works.
                        runCatching { localStorage.setItem("oms.dashboard.chartOrientation", orientation.name) }
                    }
                }
            }
            dashboard?.takeIf { !loading && !error }?.nationalSummary?.let { summary ->
                item {
                    NationalPortfolioSummary(
                        summary = summary,
                        contractTypes = dashboard?.signedContractsByType.orEmpty(),
                        onOpenSubprojects = { onOpenSubprojects(trancheNumber) },
                        onOpenMap = { onOpenMap(trancheNumber) },
                        onOpenSignedContracts = { onOpenProcurementsByStatus("contract signed", trancheNumber) },
                        onOpenFinancial = { onOpenFinancial(trancheNumber) }
                    )
                }
            }
            if (loading) {
                item { ContentState(LocalizationManager.t("loading_dashboard"), loading = true) }
                item {
                    AdaptiveChartRow(
                        first = { DashboardUpdatingChart() },
                        second = { DashboardUpdatingChart() }
                    )
                }
                item {
                    AdaptiveChartRow(
                        first = { DashboardUpdatingChart() },
                        second = { DashboardUpdatingChart() }
                    )
                }
            }
            else if (error) item { ContentState(LocalizationManager.t("error_load_dashboard"), error = true, onRetry = { reload++ }) }
            else {
                item {
                    AdaptiveChartRow(
                        first = { FundingByOblastChart(dashboard?.subprojectFunding.orEmpty(), { onOpenProjectsByRegion(it, trancheNumber) }, chartOrientation) },
                        second = {
                            SubprojectEibPaymentChart(
                                dashboard?.subprojectPaymentProgress.orEmpty(),
                                { onOpenFinancialBySubproject(it, trancheNumber) },
                                chartOrientation
                            )
                        }
                    )
                }
                item {
                    SubprojectCompletionChart(
                        dashboard?.subprojectProgress.orEmpty(),
                        chartOrientation
                    )
                }
                item {
                    AdaptiveChartRow(
                        first = {
                            MetricsChart(
                                "procurement_status_by_subprojects",
                                hintKey = "procurement_status_chart_hint",
                                metrics = dashboard?.procurementStatusCounts.orEmpty(),
                                onItemClick = { onOpenProcurementsByStatus(it, trancheNumber) },
                                orientation = chartOrientation
                            )
                        },
                        second = { MonthlyActPaymentsChart(MaterialTheme.colorScheme.primary, dashboard?.monthlyActPayments.orEmpty(), { onOpenFinancialByMonth(it, trancheNumber) }, chartOrientation, onRetry = { reload++ }) }
                    )
                }
                item {
                    val contractTypes = dashboard?.signedContractsByType.orEmpty()
                    AdaptiveChartRow(
                        first = {
                            MetricsChart(
                                "signed_contracts_by_type",
                                hintKey = "signed_contracts_by_type_hint",
                                metrics = contractTypes.map { ApiDashboardMetric(it.type, it.contractsCount) },
                                orientation = chartOrientation
                            )
                        },
                        second = {
                            MetricsChart(
                                "signed_contract_value_by_type",
                                hintKey = "signed_contract_value_by_type_hint",
                                metrics = contractTypes.map { ApiDashboardMetric(it.type, it.contractAmountUah.roundToLong()) },
                                orientation = chartOrientation
                            )
                        }
                    )
                }
                item {
                    DashboardPhotoSlider(
                        reportUuid = selectedInspection?.uuid,
                        inspectionDate = selectedInspection?.inspectionDate,
                        subprojectCode = selectedInspection?.subprojectCode,
                        photos = latestPhotos,
                        photosLoading = latestPhotosLoading,
                        photosFailed = latestPhotosFailed,
                        onRetryPhotos = { photosReload++ },
                        hasNewerInspection = selectedInspectionIndex > 0,
                        hasOlderInspection = selectedInspectionIndex < recentInspections.lastIndex,
                        onOpenNewerInspection = {
                            recentInspections.getOrNull(selectedInspectionIndex - 1)?.let {
                                selectedInspectionUuid = it.uuid
                            }
                        },
                        onOpenOlderInspection = {
                            recentInspections.getOrNull(selectedInspectionIndex + 1)?.let {
                                selectedInspectionUuid = it.uuid
                            }
                        },
                        onOpenReport = onOpenInspectionPreview
                    )
                }
            }
        }
    }
}

@Composable
private fun NationalPortfolioSummary(
    summary: ApiDashboardNationalSummary,
    contractTypes: List<ApiDashboardContractTypeMetric>,
    onOpenSubprojects: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSignedContracts: () -> Unit,
    onOpenFinancial: () -> Unit
) {
    var expanded by remember {
        mutableStateOf(runCatching { localStorage.getItem("oms.dashboard.summaryExpanded") }.getOrNull() != "false")
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .clip(MaterialTheme.shapes.extraSmall).chartControlFocus()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = LocalizationManager.t(if (expanded) "collapse" else "expand")
                    ) {
                        expanded = !expanded
                        runCatching { localStorage.setItem("oms.dashboard.summaryExpanded", expanded.toString()) }
                    }.buttonHandCursor()
                    .semantics { stateDescription = LocalizationManager.t(if (expanded) "expanded" else "collapsed") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    LocalizationManager.t("national_portfolio_summary"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded) BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = ((maxWidth + 6.dp) / 198.dp).toInt().coerceAtLeast(1)
                val metricWidth = (maxWidth - 6.dp * (columns - 1)) / columns
                FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SummaryMetric("national_subprojects", summary.subprojectsCount.toString(), cardWidth = metricWidth, onClick = onOpenSubprojects)
                SummaryMetric("national_regions", summary.regionsCount.toString(), cardWidth = metricWidth, onClick = onOpenMap)
                SummaryMetric("national_communities", summary.municipalitiesCount.toString(), cardWidth = metricWidth, onClick = onOpenMap)
                SummaryMetric("national_settlements", summary.settlementsCount.toString(), cardWidth = metricWidth, onClick = onOpenMap)
                SummaryMetric(
                    "national_approved_financing",
                    "€ ${summary.approvedFundingEur.roundToLong().grouped()}",
                    uahEquivalent = "${summary.approvedFundingUah.roundToLong().grouped()} ${LocalizationManager.currencyLabel("UAH")}",
                    cardWidth = metricWidth, onClick = onOpenSubprojects
                )
                SummaryMetric("national_signed_contracts", summary.signedContractsCount.toString(), cardWidth = metricWidth, onClick = onOpenSignedContracts)
                SummaryMetric(
                    "national_signed_contract_value",
                    "${summary.signedContractsAmountUah.roundToLong().grouped()} ${LocalizationManager.currencyLabel("UAH")}",
                    cardWidth = metricWidth, onClick = onOpenSignedContracts
                )
                listOf(
                    "dashboard_contract_works",
                    "dashboard_contract_engineer_consultant",
                    "dashboard_contract_technical_supervision"
                ).forEach { type ->
                    val amount = contractTypes.firstOrNull { it.type == type }?.contractAmountUah
                    SummaryMetric(
                        type,
                        amount?.let { "${it.roundToLong().grouped()} ${LocalizationManager.currencyLabel("UAH")}" } ?: "—",
                        cardWidth = metricWidth, onClick = onOpenSignedContracts
                    )
                }
                val legacyPayments = oms.data.WorkspaceBrand.legacySources
                val paidUah = if (legacyPayments) summary.eibPaidUnderContractsUah else summary.servicesPaidUahCents?.div(100.0)
                SummaryMetric(
                    if (legacyPayments) "eib_paid_under_contracts" else "ledger_total_paid",
                    paidUah?.roundToLong()?.let { amount ->
                        buildString {
                            append(amount.grouped())
                            append(' ')
                            append(LocalizationManager.currencyLabel("UAH"))
                        }
                    } ?: "—",
                    cardWidth = metricWidth, onClick = onOpenFinancial
                )
            }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    labelKey: String,
    value: String,
    uahEquivalent: String? = null,
    cardWidth: androidx.compose.ui.unit.Dp = 192.dp,
    onClick: (() -> Unit)? = null
) {
    OmsTooltipBox(
        tooltip = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(LocalizationManager.t(labelKey), style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (uahEquivalent != null) {
                    Text(LocalizationManager.t("national_uah_equivalent"), style = MaterialTheme.typography.labelMedium)
                    Text(uahEquivalent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) {
        Card(
            Modifier.width(cardWidth).heightIn(min = 76.dp)
                .then(if (onClick == null) Modifier else Modifier.chartControlFocus().clickable(role = Role.Button, onClick = onClick).buttonHandCursor()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    LocalizationManager.t(labelKey),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(value,
                    style = if (value.length <= 10) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            }
        }
    }
}

private fun Long.grouped(): String = formatGroupedNumber(this)

private fun Long.toEuroAmount(): String = "${(this / 100).grouped()}.${(this % 100).toString().padStart(2, '0')}"
private fun Long.toUahAmount(): String = "${(this / 100).grouped()}.${(this % 100).toString().padStart(2, '0')}"

@Composable
private fun DashboardUpdatingChart() {
    // The shared loading message announces progress once. These decorative
    // placeholders reserve a compact plot area without repeating live content.
    Card(
        Modifier.fillMaxWidth().height(156.dp).clearAndSetSemantics { },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.fillMaxWidth(.45f).height(12.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest))
                Box(Modifier.width(56.dp).height(24.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow))
            }
            Box(Modifier.fillMaxWidth().weight(1f)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow))
            Box(Modifier.fillMaxWidth(.65f).height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

/** Explicit selected state keeps the compact orientation control understandable. */
@Composable
private fun ChartOrientationPicker(selected: BarChartOrientation, onSelect: (BarChartOrientation) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(BarChartOrientation.Vertical, BarChartOrientation.Horizontal).forEach { orientation ->
            val active = orientation == selected
            val description = LocalizationManager.t(
                if (orientation == BarChartOrientation.Vertical) "vertical_charts" else "horizontal_charts"
            )
            val foreground = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            OmsTooltipBox(tooltip = { Text(description) }) {
                Box(
                    Modifier.size(32.dp).clip(MaterialTheme.shapes.extraSmall)
                        .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .chartControlFocus()
                        .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(orientation) })
                        .buttonHandCursor().semantics { contentDescription = description },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(18.dp)) {
                        listOf(.5f, 1f, .75f).forEachIndexed { index, fraction ->
                            if (orientation == BarChartOrientation.Vertical) {
                                drawRect(foreground,
                                    topLeft = Offset(size.width * index / 3f, size.height * (1f - fraction)),
                                    size = Size(size.width * .22f, size.height * fraction))
                            } else {
                                drawRect(foreground,
                                    topLeft = Offset(0f, size.height * index / 3f),
                                    size = Size(size.width * fraction, size.height * .22f))
                            }
                        }
                    }
                }
            }
        }
    }
}
