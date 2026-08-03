package oms.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.navigation.AppState
import oms.navigation.Screen
import oms.screens.*
import oms.screens.dashboard.DashboardScreen

//import oms.screens.documents.*
//import oms.screens.admin.*

// 🔹 Головний layout після логіну
// 🔹 Відповідає spec 7.2 (Sidebar layout) :contentReference[oaicite:0]{index=0}
@Composable
fun AppLayout(appState: AppState) {

    Row(modifier = Modifier.fillMaxSize()) {

        // ---------------- SIDEBAR ----------------
        Sidebar(
            currentScreen = appState.currentScreen,
            onNavigate = { appState.navigate(it) },
            onLogout = { appState.logout() }
        )
        // ---------------- CONTENT ----------------
        Box(modifier = Modifier.weight(1f)) {

            when (appState.currentScreen) {

                is Screen.Dashboard -> DashboardScreen()

                is Screen.Projects -> ProjectsScreen(
                    onOpenProject = { project -> appState.openProjectDetail(project) },
                    onCreateProject = { appState.openCreateProject() },
                    onEditProject = { appState.openEditProject(it) }
                )

                is Screen.CreateProject -> CreateProjectScreen(
                    onCancel = { appState.navigate(Screen.Projects) },
                    onCreated = { appState.navigate(Screen.Projects) }
                )

                is Screen.EditProject -> appState.selectedProject?.let { project ->
                    EditProjectScreen(
                        project = project,
                        onCancel = { appState.openProjectDetail(project) },
                        onSaved = { appState.openProjectDetail(it) }
                    )
                }

                is Screen.ProjectDetail -> {
                    appState.selectedProject?.let { project ->
                        ProjectDetailScreen(
                            project = project,
                            onBackToProjects = {
                                appState.navigate(Screen.Projects)
                            },
                            onEdit = { appState.openEditProject(it) }
                        )
                    }
                }

                is Screen.CreateInspection -> CreateInspectionScreen(
                    onSaveDraft = { },
                    onSubmit = { appState.navigate(Screen.Inspections) }
                )

                is Screen.Map -> MapScreen(
                    onOpenProject = { project ->
                        appState.openProjectDetail(project)
                    }
                )

                is Screen.Inspections -> ReportsScreen(
                    onNewInspection = { appState.openCreateInspection() }
                )

                is Screen.Financial -> FinancialScreen()

                is Screen.Documents -> DocumentsScreen()

                is Screen.Admin -> AdminScreen()

                else -> {}
            }
        }
    }
}
