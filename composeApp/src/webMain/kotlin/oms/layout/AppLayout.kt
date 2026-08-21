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
            onLogout = { appState.logout() },
            username = appState.username,
            isAdmin = appState.roleCode == "ADMIN",
            isGuest = appState.roleCode == "GUEST"
        )
        // ---------------- CONTENT ----------------
        Box(modifier = Modifier.weight(1f)) {

            when (appState.currentScreen) {

                is Screen.Dashboard -> if (appState.roleCode != "GUEST") DashboardScreen()

                is Screen.Projects -> ProjectsScreen(
                    onOpenProject = { project -> appState.openProjectDetail(project) },
                    onCreateProject = { appState.openCreateProject() },
                    onEditProject = { appState.openEditProject(it) },
                    canManageProjects = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canBulkReassign = appState.roleCode == "ADMIN"
                )

                is Screen.CreateProject -> if (appState.roleCode != "GUEST") CreateProjectScreen(
                    onCancel = { appState.navigate(Screen.Projects) },
                    onCreated = { appState.navigate(Screen.Projects) }
                )

                is Screen.EditProject -> if (appState.roleCode != "GUEST") appState.selectedProject?.let { project ->
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
                            onEdit = { appState.openEditProject(it) },
                            canDeleteProject = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                            isGuest = appState.roleCode == "GUEST"
                        )
                    }
                }

                is Screen.CreateInspection -> if (appState.roleCode != "GUEST") CreateInspectionScreen(
                    onSaveDraft = { appState.navigate(Screen.Inspections) },
                    onSubmit = { appState.navigate(Screen.Inspections) },
                    onImportXls = { appState.navigate(Screen.Inspections) }
                )

                is Screen.Map -> MapScreen(
                    onOpenProject = { project ->
                        appState.openProjectDetail(project)
                    }
                )

                is Screen.Inspections -> if (appState.roleCode != "GUEST") ReportsScreen(
                    onNewInspection = { appState.openCreateInspection() },
                    canReviewReports = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canMoveReports = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                )

                is Screen.Financial -> if (appState.roleCode != "GUEST") FinancialScreen(
                    canAccessFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canManageFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                )

                is Screen.Procurement -> if (appState.roleCode != "GUEST") ProcurementScreen(
                    canManageProcurements = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                )

                is Screen.Documents -> if (appState.roleCode != "GUEST") DocumentsScreen(
                    canManageDocuments = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                )

                is Screen.Admin -> if (appState.roleCode == "ADMIN") AdminScreen()

                else -> {}
            }
        }
    }
}
