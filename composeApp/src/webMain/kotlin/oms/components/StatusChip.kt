package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.model.ProjectStatus

/*
   Відображає статус проекту
   у вигляді кольорового chip.
*/

@Composable
fun StatusChip(status: ProjectStatus, fontWeight: FontWeight = FontWeight.Medium) {

    val (text, color) = when (status) {
        ProjectStatus.PLANNED -> LocalizationManager.t("project_status_planned") to oms.theme.OmsColors.Warning
        ProjectStatus.ACTIVE -> LocalizationManager.t("project_status_active") to oms.theme.OmsColors.Success
        ProjectStatus.SUSPENDED -> LocalizationManager.t("project_status_suspended") to oms.theme.OmsColors.Warning
        ProjectStatus.COMPLETED -> LocalizationManager.t("project_status_completed") to Color(0xFF1565C0)
        ProjectStatus.ARCHIVED -> LocalizationManager.t("project_status_archived") to Color(0xFF607D8B)
        ProjectStatus.DLP -> LocalizationManager.t("project_status_dlp") to Color(0xFF6A1B9A)
    }

    OmsBadge(text, color, fontWeight)
}
