package oms.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import oms.navigation.AppState
import oms.navigation.Screen
import oms.navigation.RouteContent
import oms.navigation.RouteContentHost
import oms.navigation.isGuestAccessible
import oms.screens.*
import oms.screens.dashboard.DashboardScreen
import kotlin.js.JsName

@JsName("fitUkraineOverview") private external fun resetMapToUkraine()

//import oms.screens.documents.*
//import oms.screens.admin.*

// 🔹 Головний layout після логіну
// 🔹 Відповідає spec 7.2 (Sidebar layout) :contentReference[oaicite:0]{index=0}
@Composable
fun AppLayout(appState: AppState) {
    // A select menu is hosted above the entire Compose tree.  If navigation
    // happens while one is open, its transparent dismiss layer would otherwise
    // remain over the new screen and make every control appear unresponsive.
    val optionOverlay = oms.components.LocalOptionOverlay.current
    LaunchedEffect(appState.navigationRevision) { optionOverlay.menu = null }
    val route = remember(appState.navigationRevision) { RouteContent.capture(appState) }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    var compact by remember(maxWidth < 1100.dp) { mutableStateOf(maxWidth < 1100.dp) }
    Row(modifier = Modifier.fillMaxSize()) {

        // ---------------- SIDEBAR ----------------
        DisableSelection {
            Sidebar(
                currentScreen = appState.currentScreen,
                onNavigate = {
                    if (it == Screen.Map && appState.currentScreen == Screen.Map) resetMapToUkraine()
                    else appState.navigate(it)
                },
                onLogout = { appState.logout() },
                username = appState.username,
                isAdmin = appState.roleCode == "ADMIN",
                isGuest = appState.roleCode == "GUEST",
                canAccessFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                compact = compact,
                onToggle = { compact = !compact }
            )
        }
        // ---------------- CONTENT ----------------
        Box(modifier = Modifier.weight(1f)) {
          RouteContentHost(route, appState.navigationRevision) { target ->
            val actionRevision = appState.navigationRevision
            // A save/delete started before navigation may finish while its old
            // screen is retained. Its completion must not redirect the new route.
            fun ifCurrent(action: () -> Unit) {
                if (appState.isAuthenticated && appState.navigationRevision == actionRevision) action()
            }
            val allowed = if (appState.roleCode == "GUEST") {
                target.screen.isGuestAccessible()
            } else when (target.screen) {
                Screen.Admin -> appState.roleCode == "ADMIN"
                Screen.CreateProject, Screen.EditProject, Screen.Financial -> appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                Screen.CreateInspection -> appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER", "INSPECTOR") || target.viewingInspection
                Screen.GuestInfo -> false
                else -> true
            }
            if (!allowed) {
                oms.navigation.ReportRouteReadiness(loading = false)
                androidx.compose.material3.Text(if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN)
                    "You do not have access to this section." else "У вас немає доступу до цього розділу.")
                return@RouteContentHost
            }
            when (target.screen) {

                is Screen.GuestInfo -> GuestInfoScreen()

                is Screen.Dashboard -> if (appState.roleCode != "GUEST") DashboardScreen(
                    onOpenProject = appState::openProjectDetail,
                    onOpenFinancial = { if (appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")) appState.navigate(Screen.Financial) },
                    onOpenProjectsByRegion = appState::openProjectsByRegion,
                    onOpenFinancialBySubproject = appState::openFinancialBySubproject,
                    onOpenProcurementsByStatus = appState::openProcurementsByStatus,
                    onOpenInspectionPreview = appState::openInspectionPreview
                )

                is Screen.Projects -> ProjectsScreen(
                    onOpenProject = { project -> appState.openProjectDetail(project) },
                    onCreateProject = { appState.openCreateProject() },
                    onEditProject = { appState.openEditProject(it) },
                    canManageProjects = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canPermanentlyDeleteProjects = appState.roleCode == "ADMIN",
                    canBulkReassign = appState.roleCode == "ADMIN",
                    requestedRegionFilter = target.region,
                    onRequestedRegionFilterConsumed = { appState.requestedProjectRegion = null }
                )

                is Screen.CreateProject -> if (appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")) CreateProjectScreen(
                    onCancel = { appState.navigate(Screen.Projects) },
                    onCreated = { ifCurrent { appState.navigate(Screen.Projects) } }
                )

                is Screen.EditProject -> if (appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")) target.project?.let { project ->
                    EditProjectScreen(
                        project = project,
                        onCancel = { appState.openProjectDetail(project) },
                        onSaved = { saved -> ifCurrent { appState.openProjectDetail(saved) } }
                    )
                }

                is Screen.ProjectDetail -> {
                    target.project?.let { project ->
                        ProjectDetailScreen(
                            project = project,
                            onBackToProjects = {
                                ifCurrent { appState.navigate(Screen.Projects) }
                            },
                            onEdit = { appState.openEditProject(it) },
                            canEditProject = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                            canDeleteProject = appState.roleCode == "ADMIN",
                            isGuest = appState.roleCode == "GUEST",
                            canAccessFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                        )
                    }
                }

                is Screen.CreateInspection -> if (appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER", "INSPECTOR") || target.viewingInspection) CreateInspectionScreen(
                    onSaveDraft = { invalidateInspectionReportsScreenCache(); ifCurrent { appState.navigate(Screen.Inspections) } },
                    onSubmit = { invalidateInspectionReportsScreenCache(); ifCurrent { appState.navigate(Screen.Inspections) } },
                    onImportXls = { invalidateInspectionReportsScreenCache(); ifCurrent { appState.navigate(Screen.Inspections) } },
                    onCancel = { appState.navigate(Screen.Inspections) },
                    currentUserName = appState.username,
                    isAdmin = appState.roleCode == "ADMIN",
                    canChangeReportStatus = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    editingReportUuid = target.inspectionUuid,
                    readOnly = target.viewingInspection
                )

                is Screen.Map -> MapScreen(
                    onOpenProject = { project ->
                        appState.openProjectDetail(project)
                    }
                )

                is Screen.Inspections -> if (appState.roleCode != "GUEST") ReportsScreen(
                    onNewInspection = { appState.openCreateInspection() },
                    onEditInspection = { appState.openEditInspection(it) },
                    onPreviewInspection = { appState.openInspectionPreview(it) },
                    canCreateReports = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER", "INSPECTOR"),
                    canReviewReports = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canMoveReports = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")
                )

                is Screen.Financial -> if (appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER")) FinancialScreen(
                    canAccessFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    canManageFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                    requestedSubprojectUuid = target.financialSubproject,
                    onRequestedSubprojectFilterConsumed = { appState.requestedFinancialSubprojectUuid = null }
                )

                is Screen.Procurement -> if (appState.roleCode != "GUEST") ProcurementScreen(
                    canManageProcurements = appState.roleCode == "ADMIN",
                    requestedStatusFilter = target.procurementStatus,
                    onRequestedStatusFilterConsumed = { appState.requestedProcurementStatus = null }
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
    }
}
