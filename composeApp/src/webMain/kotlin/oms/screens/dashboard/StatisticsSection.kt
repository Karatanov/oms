package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager


@Composable
fun StatisticsSection(
    primary: Color,
    secondary: Color,
    tertiary: Color
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            StatItem(LocalizationManager.t("total_budget"), "$12.4M", primary)
            StatItem(LocalizationManager.t("completed"), "18", secondary)
            StatItem(LocalizationManager.t("in_progress"), "7", tertiary)
            StatItem(LocalizationManager.t("delayed"), "2", MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    accent: Color
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = accent
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray
        )
    }
}