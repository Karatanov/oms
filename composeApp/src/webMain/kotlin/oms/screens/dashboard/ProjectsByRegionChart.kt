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


@Composable
fun ProjectsByRegionChart(primary: Color) {

    val data = listOf(

        BarData("Kyiv", 18f),
        BarData("Lviv", 12f),
        BarData("Odesa", 9f),
        BarData("Dnipro", 7f),
        BarData("Kharkiv", 4f)
    )

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
                text = LocalizationManager.t("projects_by_region"),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(16.dp))

            BarChart(
                data = data,
                color = primary
            )
        }
    }
}