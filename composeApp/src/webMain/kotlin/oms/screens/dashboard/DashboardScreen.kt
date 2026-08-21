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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import oms.data.ApiDashboard
import oms.data.ApiInspectionPhoto
import oms.data.ApiMonthlyActPayment
import oms.data.OmsApiClient
import oms.data.ProjectRepository

/*
   DashboardScreen

   Головний екран системи.
   Тут розміщуються:
   - графіки
*/

@Composable
fun DashboardScreen() {
    var dashboard by remember { mutableStateOf<ApiDashboard?>(null) }
    var latestPhotos by remember { mutableStateOf<List<ApiInspectionPhoto>?>(null) }
    var photoInspectionDate by remember { mutableStateOf<String?>(null) }
    var photoInspectionCode by remember { mutableStateOf<String?>(null) }
    val projects = ProjectRepository.projects
    LaunchedEffect(Unit) {
        ProjectRepository.refresh()
        dashboard = runCatching { OmsApiClient.dashboard() }.getOrNull()
    }

    val recentInspections = dashboard?.recentInspections.orEmpty().sortedByDescending { it.inspectionDate }
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 264.dp),

        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item { ProjectsByRegionChart(primary, projects) }

        item { MonthlyActPaymentsChart(primary, dashboard?.monthlyActPayments.orEmpty()) }

    }
}
