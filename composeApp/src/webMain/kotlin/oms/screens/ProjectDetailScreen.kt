package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.components.StatusChip
import oms.localization.LocalizationManager
import oms.model.Project
import oms.navigation.Screen

@Composable
fun ProjectDetailScreen(
    project: Project,
    onBackToProjects: () -> Unit = {},
    onEdit: (Project) -> Unit = {}
) {
    var selectedTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(ProjectDetailTab.GeneralInfo) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Breadcrumb
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackToProjects) {
                Text(Screen.Projects.title)
            }

            Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                text = project.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }

        // Header
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatusChip(project.status)

                            Text(
                                text = "${LocalizationManager.t("address")}: ${project.region}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = "${LocalizationManager.t("sector")}: Infrastructure • ${LocalizationManager.t("construction_type")}: General",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(onClick = { onEdit(project) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = LocalizationManager.t("edit")
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(LocalizationManager.t("edit"))
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DetailMetricCard(
                title = LocalizationManager.t("budget_planned"),
                value = "$1.2M",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("amount_spent"),
                value = "$840K",
                modifier = Modifier.weight(1f)
            )
            DetailMetricCard(
                title = LocalizationManager.t("budget_remaining"),
                value = "$360K",
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                ProjectDetailTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp)
            ) {
                when (selectedTab) {
                    ProjectDetailTab.GeneralInfo -> PlaceholderTabContent(LocalizationManager.t("general_info"))
                    ProjectDetailTab.InspectionReports -> PlaceholderTabContent(LocalizationManager.t("inspection_reports_tab"))
                    ProjectDetailTab.Financials -> PlaceholderTabContent(LocalizationManager.t("financials"))
                    ProjectDetailTab.Documents -> PlaceholderTabContent(LocalizationManager.t("documents_tab"))
                    ProjectDetailTab.Incidents -> PlaceholderTabContent(LocalizationManager.t("incidents"))
                }
            }
        }
    }
}

@Composable
private fun DetailMetricCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

@Composable
private fun PlaceholderTabContent(title: String) {
    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$title — ${LocalizationManager.t("coming_next")}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class ProjectDetailTab(val title: String) {
    GeneralInfo("General Info"),
    InspectionReports("Inspection Reports"),
    Financials("Financials"),
    Documents("Documents"),
    Incidents("Incidents (HSE)")
}