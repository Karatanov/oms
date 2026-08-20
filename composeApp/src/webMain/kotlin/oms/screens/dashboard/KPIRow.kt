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
        title = LocalizationManager.t("active_projects"),
        value = dashboard?.projectsActive?.toString() ?: "—",
        icon = Icons.Default.Folder,
        color = primary
    )
    KPICard(
        title = LocalizationManager.t("completed_this_month"),
        value = dashboard?.projectsCompletedThisMonth?.toString() ?: "—",
        icon = Icons.Default.Description,
        color = secondary
    )
    KPICard(LocalizationManager.t("pending_inspections"), dashboard?.pendingInspections?.toString() ?: "—", Icons.Default.Description, secondary)
    KPICard(LocalizationManager.t("total_budget"), dashboard?.budgetPlanned?.toString() ?: "—", Icons.Default.Folder, primary)
}
