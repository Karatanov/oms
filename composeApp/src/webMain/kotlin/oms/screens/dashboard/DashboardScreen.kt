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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.components.*
import oms.data.*
import oms.localization.LocalizationManager
import oms.screens.*

@Composable
fun DashboardScreen(onOpenProject: (oms.model.Project) -> Unit = {}, onOpenFinancial: () -> Unit = {}) {
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    val projects = ProjectRepository.projects
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        loading = true; error = false
        runCatching {
            coroutineScope {
                val refresh = async { ProjectRepository.refresh() }
                val result = async { OmsApiClient.dashboard() }
                refresh.await()
                result.await()
            }
        }.onSuccess { dashboard = it }.onFailure { error = true }
        loading = false
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
                    LocalizationManager.t("portfolio_overview"))
            }
            if (loading) item { ContentState(LocalizationManager.t("loading_dashboard"), loading = true) }
            else if (error) item { ContentState(LocalizationManager.t("error_load_dashboard"), error = true, onRetry = { reload++ }) }
            else {
                item {
                    AdaptiveChartRow(
                        first = { FundingByOblastChart(dashboard?.subprojectFunding.orEmpty()) },
                        second = { SubprojectProgressChart(dashboard?.subprojectProgress.orEmpty()) { uuid -> projects.firstOrNull { it.id == uuid }?.let(onOpenProject) } }
                    )
                }
                item {
                    AdaptiveChartRow(
                        first = { MetricsChart("procurement_status_by_subprojects", metrics = dashboard?.procurementStatusCounts.orEmpty()) },
                        second = { MonthlyActPaymentsChart(MaterialTheme.colorScheme.primary, dashboard?.monthlyActPayments.orEmpty(), onOpenFinancial) }
                    )
                }
                item { DashboardPhotoSlider(latest?.inspectionDate, latest?.inspectionCode, latestPhotos) }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TableActionIconButton(LocalizationManager.t("dashboard_scroll_up"), Icons.Default.KeyboardArrowUp) { scope.launch { scrollState.animateScrollBy(-500f) } }
            TableActionIconButton(LocalizationManager.t("dashboard_scroll_down"), Icons.Default.KeyboardArrowDown) { scope.launch { scrollState.animateScrollBy(500f) } }
        }
    }
}
