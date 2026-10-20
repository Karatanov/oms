package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
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
@JsName("setOmsMobileNavigationOpen") private external fun setMobileNavigationOpen(open: Boolean)

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
    val tableNavigator = oms.components.LocalTableScrollOverlay.current
    LaunchedEffect(appState.navigationRevision) { optionOverlay.menu = null }
    val route = remember(appState.navigationRevision) { RouteContent.capture(appState) }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val mobile = maxWidth < 768.dp || (maxHeight < 500.dp && maxWidth < 1100.dp)
    var navigationOpen by remember { mutableStateOf(false) }
    LaunchedEffect(appState.navigationRevision, mobile) { navigationOpen = false }
    DisposableEffect(mobile && navigationOpen) {
        setMobileNavigationOpen(mobile && navigationOpen)
        // Horizontal table controls remain available on phones; only hide
        // them while the navigation drawer itself is open.
        tableNavigator.hidden = navigationOpen
        onDispose { setMobileNavigationOpen(false); tableNavigator.hidden = false }
    }
    var compact by remember(maxWidth < 1100.dp) { mutableStateOf(maxWidth < 1100.dp) }
    @Composable fun NavigationSidebar() {
        DisableSelection {
            Sidebar(
                currentScreen = appState.currentScreen,
                onNavigate = {
                    navigationOpen = false
                    if (it == Screen.Map && appState.currentScreen == Screen.Map) resetMapToUkraine()
                    else appState.navigate(it)
                },
                onLogout = { appState.logout() },
                username = appState.username,
                isAdmin = appState.roleCode == "ADMIN",
                isGuest = appState.roleCode == "GUEST",
                canAccessFinancials = appState.roleCode in setOf("ADMIN", "PROJECT_MANAGER"),
                compact = if (mobile) false else compact,
                onToggle = { if (mobile) navigationOpen = false else compact = !compact },
                toggleLabel = if (mobile) oms.localization.LocalizationManager.t("close") else null
            )
        }
    }
    CompositionLocalProvider(LocalMobileLayout provides mobile) {
    Column(Modifier.fillMaxSize()) {
    if (mobile) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { optionOverlay.menu = null; navigationOpen = true }, modifier = Modifier.semantics { testTag = "mobile-navigation-toggle" }) {
                Icon(Icons.Default.Menu, oms.localization.LocalizationManager.t("expand_navigation"))
            }
            Text("UMITAF OMS", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = oms.localization.LocalizationManager::switchLanguage) {
                Icon(Icons.Default.Language, oms.localization.LocalizationManager.t("language"))
            }
        }
    }
    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
        if (!mobile) NavigationSidebar()
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
                else -> true
            }
            if (!allowed) {
                oms.navigation.ReportRouteReadiness(loading = false)
                androidx.compose.material3.Text(if (oms.localization.LocalizationManager.currentLanguage == oms.localization.Language.EN)
                    "You do not have access to this section." else "У вас немає доступу до цього розділу.")
                return@RouteContentHost
            }
            when (target.screen) {

                is Screen.Dashboard -> DashboardScreen(
                    onOpenProject = if (appState.roleCode == "GUEST") ({}) else appState::openProjectDetail,
                    onOpenFinancialByMonth = if (appState.roleCode == "GUEST") ({}) else appState::openFinancialByMonth,
                    onOpenFinancialByRegion = if (appState.roleCode == "GUEST") ({}) else appState::openFinancialByRegion,
                    onOpenFinancialBySubproject = if (appState.roleCode == "GUEST") ({}) else appState::openFinancialBySubproject,
                    onOpenProcurementsByStatus = if (appState.roleCode == "GUEST") ({}) else appState::openProcurementsByStatus,
                    onOpenInspectionPreview = if (appState.roleCode == "GUEST") ({}) else appState::openInspectionPreview
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
                    canUpdatePaymentMonitoring = appState.roleCode == "ADMIN",
                    requestedSubprojectUuid = target.financialSubproject,
                    requestedRegion = target.financialRegion,
                    requestedMonth = target.financialMonth,
                    onRequestedSubprojectFilterConsumed = { appState.requestedFinancialSubprojectUuid = null },
                    onRequestedRegionFilterConsumed = { appState.requestedFinancialRegion = null },
                    onRequestedMonthFilterConsumed = { appState.requestedFinancialMonth = null }
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
    if (mobile && navigationOpen) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(Unit) { requester.requestFocus() }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = .4f))
            .clickable(onClickLabel = oms.localization.LocalizationManager.t("close")) { navigationOpen = false })
        Box(Modifier.fillMaxHeight().focusRequester(requester).onPreviewKeyEvent {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { navigationOpen = false; true } else false
        }.focusable().semantics { paneTitle = oms.localization.LocalizationManager.t("expand_navigation") }) {
            NavigationSidebar()
        }
    }
    }
    }
}
