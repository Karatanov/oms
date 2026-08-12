package oms.screens.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.charts.BarData
import oms.charts.VerticalBarChart
import oms.localization.LocalizationManager
import oms.model.Project

@Composable
fun ProjectsByRegionChart(primary: Color, projects: List<Project>) {
    val data = projects
        .mapNotNull { it.startDate?.takeIf { date -> date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.take(7) }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedBy { it.first }
        .map { (month, count) -> BarData(month, count.toFloat()) }

    Card(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LocalizationManager.t("projects_by_start_month"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_projects_with_start_date"))
            else VerticalBarChart(data = data, color = primary)
        }
    }
}
