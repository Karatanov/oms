package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.charts.BarChart
import oms.charts.BarData
import oms.localization.LocalizationManager
import oms.model.Project


@Composable
fun ProjectsByRegionChart(primary: Color, projects: List<Project>) {

    val data = projects
        .filter { it.projectType.equals("subproject", ignoreCase = true) }
        .groupingBy { it.region.ifBlank { "—" } }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .map { (region, count) -> BarData(region, count.toFloat()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),

        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = LocalizationManager.t("subprojects"),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            if (data.isEmpty()) Text(LocalizationManager.t("no_subprojects"))
            else BarChart(data = data, color = primary)
        }
    }
}
