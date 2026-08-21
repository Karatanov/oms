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
    val subprojects = projects.filter { it.projectType.equals("subproject", ignoreCase = true) }
    val data = subprojects
        .mapNotNull { it.startDate?.takeIf { date -> date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }?.take(7) }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedBy { it.first }
        .map { (month, count) -> BarData(month.toStartMonthLabel(), count.toFloat()) }

    Card(modifier = Modifier.fillMaxWidth().height(320.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(LocalizationManager.t("projects_by_start_month"), style = MaterialTheme.typography.titleMedium)
            Text(
                LocalizationManager.t("subprojects_count").replace("{count}", subprojects.size.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                LocalizationManager.t("start_month_axis_label"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (data.isEmpty()) Text(LocalizationManager.t("no_projects_with_start_date"))
            else VerticalBarChart(data = data, color = primary)
        }
    }
}

internal fun String.toStartMonthLabel(): String {
    val year = take(4)
    return "${toMonthName()} $year"
}

internal fun String.toMonthName(): String {
    val month = takeLast(2).toIntOrNull() ?: return this
    val monthKey = when (month) {
        1 -> "month_january"; 2 -> "month_february"; 3 -> "month_march"; 4 -> "month_april"
        5 -> "month_may"; 6 -> "month_june"; 7 -> "month_july"; 8 -> "month_august"
        9 -> "month_september"; 10 -> "month_october"; 11 -> "month_november"; 12 -> "month_december"
        else -> return this
    }
    return LocalizationManager.t(monthKey)
}
