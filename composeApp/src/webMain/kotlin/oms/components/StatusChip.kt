package oms.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import oms.localization.LocalizationManager
import oms.model.ProjectStatus

/*
   Відображає статус проекту
   у вигляді кольорового chip.
*/

@Composable
fun StatusChip(status: ProjectStatus) {

    val (text, color) = when (status) {
        ProjectStatus.PLANNED -> LocalizationManager.t("project_status_planned") to Color(0xFFF9A825)
        ProjectStatus.ACTIVE -> LocalizationManager.t("project_status_active") to Color(0xFF2E7D32)
        ProjectStatus.SUSPENDED -> LocalizationManager.t("project_status_suspended") to Color(0xFFEF6C00)
        ProjectStatus.COMPLETED -> LocalizationManager.t("project_status_completed") to Color(0xFF1565C0)
        ProjectStatus.ARCHIVED -> LocalizationManager.t("project_status_archived") to Color(0xFF607D8B)
        ProjectStatus.DLP -> LocalizationManager.t("project_status_dlp") to Color(0xFF6A1B9A)
    }

    AssistChip(

        onClick = { },

        label = {
            Text(text)
        },

        colors = AssistChipDefaults.assistChipColors(

            labelColor = Color.White,

            containerColor = color
        )
    )
}
