package oms.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import oms.data.ApiDashboard
import oms.data.ApiInspectionPhoto
import oms.data.ApiMonthlyActPayment
import oms.data.OmsApiClient
import oms.data.ProjectRepository
import oms.localization.LocalizationManager
import oms.screens.FundingByOblastChart
import oms.screens.SubprojectProgressChart
import oms.screens.MetricsChart

/*
   DashboardScreen

   Головний екран системи.
   Тут розміщуються:
   - графіки
*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpenProject: (oms.model.Project) -> Unit = {}, onOpenFinancial: () -> Unit = {}) {
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    var inspectionReports by remember { mutableStateOf<List<oms.data.ApiInspectionReport>>(emptyList()) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    var photoInspectionDate by remember { mutableStateOf<String?>(null) }
    var photoInspectionCode by remember { mutableStateOf<String?>(null) }
    val projects = ProjectRepository.projects
    val scrollState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        coroutineScope {
            val refreshProjects = async { ProjectRepository.refresh() }
            val loadDashboard = async { runCatching { OmsApiClient.dashboard() }.getOrNull() }
            val loadReports = async { runCatching { OmsApiClient.inspectionReports().map { it.report } }.getOrDefault(emptyList()) }
            refreshProjects.await()
            dashboard = loadDashboard.await()
            inspectionReports = loadReports.await()
        }
    }

    val recentInspections = inspectionReports.sortedByDescending { it.inspectionDate }
    LaunchedEffect(recentInspections.map { it.uuid }) {
        latestPhotos = null
        photoInspectionDate = recentInspections.firstOrNull()?.inspectionDate
        photoInspectionCode = recentInspections.firstOrNull()?.inspectionCode
        val photosByInspection = coroutineScope {
            recentInspections.map { inspection ->
                async {
                    inspection to runCatching { OmsApiClient.inspectionPhotos(inspection.uuid) }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        for ((inspection, photos) in photosByInspection) {
            if (photos.isNotEmpty()) {
                latestPhotos = photos
                photoInspectionDate = inspection.inspectionDate
                photoInspectionCode = inspection.inspectionCode
                return@LaunchedEffect
            }
        }
        latestPhotos = emptyList()
    }
    DashboardPhotoSlider(photoInspectionDate, photoInspectionCode, latestPhotos)

    val primary = MaterialTheme.colorScheme.primary

    fun scrollBy(delta: Float) = scrollScope.launch { scrollState.animateScrollBy(delta) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = scrollState,
        modifier = Modifier
            .fillMaxSize()
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> { scrollBy(-420f); true }
                    Key.DirectionDown -> { scrollBy(420f); true }
                    Key.PageUp -> { scrollBy(-720f); true }
                    Key.PageDown -> { scrollBy(720f); true }
                    Key.MoveHome -> { scrollScope.launch { scrollState.animateScrollToItem(0) }; true }
                    Key.MoveEnd -> { scrollScope.launch { scrollState.animateScrollToItem((scrollState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) }; true }
                    else -> false
                }
            }
            .padding(start = 24.dp, top = 24.dp, end = 76.dp, bottom = 264.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item { FundingByOblastChart(dashboard?.subprojectFunding.orEmpty()) }

        item {
            SubprojectProgressChart(dashboard?.subprojectProgress.orEmpty()) { uuid ->
                projects.firstOrNull { it.id == uuid }?.let(onOpenProject)
            }
        }

        item { MetricsChart("procurement_status_by_subprojects", metrics = dashboard?.procurementStatusCounts.orEmpty()) }

        item { MonthlyActPaymentsChart(primary, dashboard?.monthlyActPayments.orEmpty(), onOpenFinancial) }

    }
    Column(
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp, bottom = 264.dp),
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
    }
}
