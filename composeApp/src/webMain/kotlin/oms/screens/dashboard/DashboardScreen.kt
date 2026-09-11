package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import oms.components.*
import oms.data.*
import oms.localization.LocalizationManager
import oms.screens.*
import oms.charts.BarChartOrientation

@Composable
fun DashboardScreen(
    onOpenProject: (oms.model.Project) -> Unit = {},
    onOpenFinancial: () -> Unit = {},
    onOpenProjectsByRegion: (String) -> Unit = {},
    onOpenFinancialBySubproject: (String) -> Unit = {},
    onOpenProcurementsByStatus: (String) -> Unit = {}
) {
    var dashboard by remember { mutableStateOf<ApiDashboardOverview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    var chartOrientation by remember { mutableStateOf(BarChartOrientation.Vertical) }
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        loading = true; error = false
        // Yield a frame first: the authenticated workspace and its navigation
        // become interactive before the remote overview begins loading.
        yield()
        // A free Render service may still be waking up when the first request
        // is made.  Do not turn the dashboard into an empty error screen after
        // that transient failure: keep the chart placeholders visible and retry.
        try {
            repeat(4) { attempt ->
                try {
                    dashboard = OmsApiClient.dashboardOverview()
                    return@LaunchedEffect
                } catch (cancelled: CancellationException) {
                    // Leaving Dashboard cancels this effect and aborts the pending fetch.
                    throw cancelled
                } catch (_: Exception) {
                    if (attempt < 3) delay((attempt + 1) * 2_000L) else error = true
                }
            }
        } finally {
            loading = false
        }
    }
    val latest = dashboard?.recentInspections?.maxByOrNull { it.inspectionDate }
    LaunchedEffect(latest?.uuid) {
        latestPhotos = null
        latestPhotos = latest?.let { runCatching { OmsApiClient.inspectionPhotos(it.uuid) }.getOrDefault(emptyList()) } ?: emptyList()
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
            contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 68.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                PageHeading(LocalizationManager.t("dashboard"), Icons.Default.Dashboard,
                    LocalizationManager.t("portfolio_overview")) {
                    OutlinedButton(onClick = {
                        chartOrientation = if (chartOrientation == BarChartOrientation.Vertical) BarChartOrientation.Horizontal else BarChartOrientation.Vertical
                    }) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Spacer(Modifier.width(6.dp))
                        Text(LocalizationManager.t(if (chartOrientation == BarChartOrientation.Vertical) "horizontal_charts" else "vertical_charts"))
                    }
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
                        second = { SubprojectProgressChart(dashboard?.subprojectProgress.orEmpty(), onOpenFinancialBySubproject, chartOrientation) }
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
                        second = { MonthlyActPaymentsChart(MaterialTheme.colorScheme.primary, dashboard?.monthlyActPayments.orEmpty(), onOpenFinancial, chartOrientation) }
                    )
                }
                item { DashboardPhotoSlider(latest?.inspectionDate, latestPhotos) }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp, scrollState, -1, clickDistance = 500f)
            oms.components.HoldToScrollButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown, scrollState, 1, clickDistance = 500f)
        }
    }
}

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
