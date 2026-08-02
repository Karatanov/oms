package oms.screens.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import oms.localization.LocalizationManager
import oms.data.ApiDashboard


@Composable
fun KPIRow(
    primary: Color,
    secondary: Color,
    error: Color,
    dashboard: ApiDashboard?
) {
    KPICard(
        title = LocalizationManager.t("projects"),
        value = dashboard?.projectsTotal?.toString() ?: "—",
        icon = Icons.Default.Folder,
        color = primary,
        trend = listOf(10f, 12f, 14f, 18f, 22f, 30f, 42f)
    )

    KPICard(
        title = LocalizationManager.t("reports"),
        value = dashboard?.inspectionsTotal?.toString() ?: "—",
        icon = Icons.Default.Description,
        color = secondary,
        trend = listOf(40f, 48f, 60f, 75f, 92f, 110f, 126f)
    )

    KPICard(
        title = LocalizationManager.t("issues"),
        value = dashboard?.findingsTotal?.toString() ?: "—",
        icon = Icons.Default.Warning,
        color = error,
        trend = listOf(12f, 11f, 9f, 10f, 8f, 6f, 5f)
    )
}
