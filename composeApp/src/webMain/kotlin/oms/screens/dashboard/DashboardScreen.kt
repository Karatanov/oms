package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.SwapHoriz
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
    onOpenFinancialByMonth: (String) -> Unit = {},
    onOpenProjectsByRegion: (String) -> Unit = {},
    onOpenFinancialBySubproject: (String) -> Unit = {},
    onOpenProcurementsByStatus: (String) -> Unit = {},
    onOpenSubprojects: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenFinancial: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    androidx.compose.material3.OutlinedButton(onClick = {
                        chartOrientation = if (chartOrientation == BarChartOrientation.Vertical) BarChartOrientation.Horizontal else BarChartOrientation.Vertical
                    }, modifier = Modifier.buttonHandCursor()) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Spacer(Modifier.width(6.dp))
                        Text(LocalizationManager.t(if (chartOrientation == BarChartOrientation.Vertical) "horizontal_charts" else "vertical_charts"))
                    }
                }
            }
            dashboard?.nationalSummary?.let { summary ->
                item {
                    NationalPortfolioSummary(
                        summary = summary,
                        onOpenSubprojects = onOpenSubprojects,
                        onOpenMap = onOpenMap,
                        onOpenSignedContracts = { onOpenProcurementsByStatus("contract signed") },
                        onOpenFinancial = onOpenFinancial
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
                        first = { FundingByOblastChart(dashboard?.subprojectFunding.orEmpty(), onOpenProjectsByRegion, chartOrientation) },
                        second = {
                            SubprojectEibPaymentChart(
                                dashboard?.subprojectPaymentProgress.orEmpty(),
                                onOpenFinancialBySubproject,
                                chartOrientation
                            )
                        }
                    )
                }
                item {
                    AdaptiveChartRow(
                        first = {
                            MetricsChart(
                                "procurement_status_by_subprojects",
                                metrics = dashboard?.procurementStatusCounts.orEmpty(),
                                onItemClick = onOpenProcurementsByStatus,
                                orientation = chartOrientation
                            )
                        },
                        second = { MonthlyActPaymentsChart(MaterialTheme.colorScheme.primary, dashboard?.monthlyActPayments.orEmpty(), onOpenFinancialByMonth, chartOrientation) }
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
    onOpenSubprojects: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSignedContracts: () -> Unit,
    onOpenFinancial: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                LocalizationManager.t("national_portfolio_summary"),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetric("national_subprojects", summary.subprojectsCount.toString(), onClick = onOpenSubprojects)
                SummaryMetric("national_regions", summary.regionsCount.toString(), onClick = onOpenMap)
                SummaryMetric("national_communities", summary.municipalitiesCount.toString(), onClick = onOpenMap)
                SummaryMetric("national_settlements", summary.settlementsCount.toString(), onClick = onOpenMap)
                SummaryMetric(
                    "national_approved_financing",
                    "€ ${summary.approvedFundingEur.roundToLong().grouped()}",
                    uahEquivalent = "${summary.approvedFundingUah.roundToLong().grouped()} UAH",
                    onClick = onOpenSubprojects
                )
                SummaryMetric("national_signed_contracts", summary.signedContractsCount.toString(), onClick = onOpenSignedContracts)
                SummaryMetric(
                    "national_signed_contract_value",
                    "${summary.signedContractsAmountUah.roundToLong().grouped()} UAH",
                    onClick = onOpenSignedContracts
                )
                SummaryMetric(
                    "eib_paid_under_contracts",
                    summary.eibPaidUnderContractsUah?.roundToLong()?.let { "${it.grouped()} грн" } ?: "—",
                    onClick = onOpenFinancial
                )
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    labelKey: String,
    value: String,
    uahEquivalent: String? = null,
    onClick: (() -> Unit)? = null
) {
    // A summary should be scannable at a glance.  Each metric gets its own
    // compact card, while the secondary UAH figure remains available on hover
    // or press instead of making the main number compete with a second currency.
    OmsTooltipBox(
        enabled = uahEquivalent != null,
        openOnPress = true,
        tooltip = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(LocalizationManager.t("national_uah_equivalent"), style = MaterialTheme.typography.labelMedium)
                Text(uahEquivalent.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    ) {
        Card(
            Modifier.widthIn(min = 148.dp, max = 208.dp).heightIn(min = 88.dp)
                .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick).buttonHandCursor()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    LocalizationManager.t(labelKey),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
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
