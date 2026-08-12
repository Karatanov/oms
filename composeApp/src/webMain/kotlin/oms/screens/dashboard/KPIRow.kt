package oms.screens.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import oms.data.ApiDashboard
import oms.localization.LocalizationManager

@Composable
fun KPIRow(primary: Color, secondary: Color, dashboard: ApiDashboard?) {
    KPICard(
        title = LocalizationManager.t("projects_subprojects"),
        value = dashboard?.projectsTotal?.toString() ?: "—",
        icon = Icons.Default.Folder,
        color = primary
    )
    KPICard(
        title = LocalizationManager.t("reports"),
        value = dashboard?.inspectionsTotal?.toString() ?: "—",
        icon = Icons.Default.Description,
        color = secondary
    )
}
