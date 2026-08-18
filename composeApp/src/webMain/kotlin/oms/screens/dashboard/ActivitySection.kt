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
import oms.data.ApiActivity


@Composable
fun ActivitySection(activities: List<ApiActivity>) {

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

            if (activities.isEmpty()) {
                Text(LocalizationManager.t("no_recent_activity"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                activities.forEach { activity ->
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    ActivityRow(activity.action.toActivityLabel(), activity.userLogin, activity.createdAt.replace('T', ' '))
                }
            }
        }
    }
}

private fun String.toActivityLabel(): String = when (this) {
    "project_created" -> LocalizationManager.t("activity_project_created")
    "project_deleted" -> LocalizationManager.t("activity_project_deleted")
    "inspection_imported" -> LocalizationManager.t("activity_inspection_imported")
    "financial_record_created" -> LocalizationManager.t("activity_financial_created")
    "financial_records_imported" -> LocalizationManager.t("activity_financial_imported")
    else -> this
}
