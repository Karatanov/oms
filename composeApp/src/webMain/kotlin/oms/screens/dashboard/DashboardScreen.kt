package oms.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.data.ApiDashboard
import oms.data.ApiInspectionPhoto
import oms.data.OmsApiClient

/*
   DashboardScreen

   Головний екран системи.
   Тут розміщуються:
   - KPI картки
   - графіки
   - останні активності
*/

@Composable
fun DashboardScreen() {
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    LaunchedEffect(Unit) { dashboard = runCatching { OmsApiClient.dashboard() }.getOrNull() }

    val latestInspection = dashboard?.recentInspections
        ?.maxByOrNull { it.inspectionDate }
    LaunchedEffect(latestInspection?.uuid) {
        latestPhotos = latestInspection?.let {
            runCatching { OmsApiClient.inspectionPhotos(it.uuid) }.getOrDefault(emptyList())
        }
    }
    DashboardPhotoSlider(latestInspection?.inspectionDate, latestPhotos)

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 264.dp, end = 24.dp, bottom = 24.dp),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item { KPIRow(primary, secondary, error, dashboard) }

        item { ProjectsByRegionChart(primary) }

        item { StatisticsSection(primary, secondary, tertiary) }

        item { ActivitySection() }
    }
}
