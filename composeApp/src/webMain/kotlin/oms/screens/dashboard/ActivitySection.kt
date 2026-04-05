package oms.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager


@Composable
fun ActivitySection() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = LocalizationManager.t("recent_activity"),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            ActivityRow(
                LocalizationManager.t("project_updated"),
                LocalizationManager.t("time_2_hours_ago")
            )
            ActivityRow(
                LocalizationManager.t("inspection_completed"),
                LocalizationManager.t("time_5_hours_ago")
            )
            ActivityRow(
                LocalizationManager.t("new_report_uploaded"),
                LocalizationManager.t("time_yesterday")
            )
        }
    }
}
