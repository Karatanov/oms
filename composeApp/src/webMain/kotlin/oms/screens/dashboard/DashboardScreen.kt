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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
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
    var chartOrientation by remember { mutableStateOf(BarChartOrientation.Vertical) }
    var trancheNumber by remember { mutableStateOf<Int?>(null) }
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
    LaunchedEffect(selectedInspection?.uuid) {
        latestPhotos = null
        latestPhotosLoading = selectedInspection != null
        try {
            latestPhotos = selectedInspection?.let {
                runCatching { OmsApiClient.inspectionPhotos(it.uuid) }.getOrDefault(emptyList())
            } ?: emptyList()
        } finally {
            latestPhotosLoading = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.PageUp -> { scope.launch { scrollState.animateScrollBy(-600f) }; true }
                Key.PageDown -> { scope.launch { scrollState.animateScrollBy(600f) }; true }
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
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.widthIn(max = 500.dp).fillMaxWidth()) {
                        listOf<Int?>(null, 1, 2).forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = trancheNumber == option,
                                onClick = { trancheNumber = option },
                                modifier = Modifier.buttonHandCursor(),
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                colors = dashboardSegmentedColors(),
                                icon = {},
                                label = {
                                    Text(LocalizationManager.t(when (option) {
                                        null -> "all_tranches"
                                        1 -> "tranche_a"
                                        else -> "tranche_b"
                                    }))
                                }
                            )
                        }
                    }
                    ChartOrientationPicker(chartOrientation) { chartOrientation = it }
                }
            }
            dashboard?.nationalSummary?.let { summary ->
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
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                LocalizationManager.t("national_portfolio_summary"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = ((maxWidth + 8.dp) / 216.dp).toInt().coerceAtLeast(1)
                val metricWidth = (maxWidth - 8.dp * (columns - 1)) / columns
                FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                SummaryMetric(
                    "eib_paid_under_contracts",
                    summary.eibPaidUnderContractsUah?.roundToLong()?.let { amount ->
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
    cardWidth: androidx.compose.ui.unit.Dp = 208.dp,
    onClick: (() -> Unit)? = null
) {
    // A summary should be scannable at a glance.  Each metric gets its own
    // compact card, while the secondary UAH figure remains available on hover
    // or press instead of making the main number compete with a second currency.
    OmsTooltipBox(
        openOnPress = true,
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
            Modifier.width(cardWidth).height(112.dp)
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).buttonHandCursor()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    LocalizationManager.t(labelKey),
                    modifier = Modifier.height(48.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            }
        }
    }
}

private fun Long.grouped(): String = formatGroupedNumber(this)

private fun Long.toEuroAmount(): String = "${(this / 100).grouped()}.${(this % 100).toString().padStart(2, '0')}"
private fun Long.toUahAmount(): String = "${(this / 100).grouped()}.${(this % 100).toString().padStart(2, '0')}"

@Composable
private fun dashboardSegmentedColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primary,
    activeContentColor = Color.White,
    inactiveContainerColor = MaterialTheme.colorScheme.surface,
    inactiveContentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
private fun DashboardUpdatingChart() {
    Card(Modifier.fillMaxWidth().heightIn(min = 220.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(LocalizationManager.t("loading_dashboard"), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
