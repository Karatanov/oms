package oms.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oms.model.Project
import oms.data.ProjectRepository
import kotlin.js.JsName

@JsName("pushOmsRoute") private external fun pushOmsRoute(route: String)
@JsName("replaceOmsRoute") private external fun replaceOmsRoute(route: String)

// 🔹 Центральний стан усього додатку
// 🔹 Зберігає:
//    - поточний екран
//    - авторизацію
//    - токен
class AppState {

    // 🔹 Поточний екран
    var currentScreen by mutableStateOf<Screen>(Screen.Login)

    // 🔹 Вибраний проєкт для detail page
    var selectedProject by mutableStateOf<Project?>(null)

    // One-shot filter passed from dashboard analytics to the project registry.
    var requestedProjectRegion by mutableStateOf<String?>(null)
    var requestedFinancialSubprojectUuid by mutableStateOf<String?>(null)
    var requestedProcurementStatus by mutableStateOf<String?>(null)
    var editingInspectionUuid by mutableStateOf<String?>(null)

    // 🔹 Чи авторизований користувач
    var isAuthenticated by mutableStateOf(false)

    // Browser authentication is held by the HttpOnly server session cookie.
    // This marker is UI state only; bearer credentials are deliberately not persisted.
    var token: String? by mutableStateOf(null)
    var username by mutableStateOf("")
    var roleCode by mutableStateOf("")

    // ---------------- NAVIGATION ----------------

    // 🔹 Перехід між екранами
    fun navigate(screen: Screen) {
        if (currentScreen != screen) pushOmsRoute(screen.route())
        currentScreen = screen
    }

    fun restoreRoute(route: String) {
        val screen = when (route.substringBefore('/')) {
            "dashboard" -> Screen.Dashboard
            "projects" -> Screen.Projects
            "project-new" -> Screen.CreateProject
            "project-edit" -> Screen.EditProject
            "project" -> Screen.ProjectDetail
            "inspection-new" -> Screen.CreateInspection
            "map" -> Screen.Map
            "inspections" -> Screen.Inspections
            "financial" -> Screen.Financial
            "procurement" -> Screen.Procurement
            "documents" -> Screen.Documents
            "admin" -> Screen.Admin
            else -> if (roleCode == "GUEST") Screen.Map else Screen.Dashboard
        }
        if ((screen == Screen.ProjectDetail || screen == Screen.EditProject) && selectedProject == null) currentScreen = Screen.Projects
        else currentScreen = screen
    }

    // 🔹 Відкрити detail-екран конкретного проєкту
    fun openProjectDetail(project: Project) {
        selectedProject = project
        navigate(Screen.ProjectDetail)
    }

    // 🔹 Відкрити створення інспекції
    fun openCreateInspection() {
        editingInspectionUuid = null
        navigate(Screen.CreateInspection)
    }

    fun openEditInspection(reportUuid: String) {
        editingInspectionUuid = reportUuid
        navigate(Screen.CreateInspection)
    }

    fun openCreateProject() {
        navigate(Screen.CreateProject)
    }

    fun openProjectsByRegion(region: String) {
        requestedProjectRegion = region
        navigate(Screen.Projects)
    }

    fun openFinancialBySubproject(subprojectUuid: String) {
        requestedFinancialSubprojectUuid = subprojectUuid
        navigate(Screen.Financial)
    }

    fun openProcurementsByStatus(status: String) {
        requestedProcurementStatus = status
        navigate(Screen.Procurement)
    }

    fun openEditProject(project: Project) {
        selectedProject = project
        navigate(Screen.EditProject)
    }

    // 🔹 Успішний логін
    fun onLoginSuccess(username: String, roleCode: String) {
        this.token = "session"
        this.username = username
        this.roleCode = roleCode
        isAuthenticated = true

        // 🔹 Після логіну завжди відкриваємо Dashboard
        currentScreen = Screen.Dashboard
        replaceOmsRoute(Screen.Dashboard.route())
    }

    /** Restores a verified HttpOnly session without redirecting away from the current route. */
    fun restoreAuthenticatedSession(username: String, roleCode: String, route: String) {
        token = "session"
        this.username = username
        this.roleCode = roleCode
        isAuthenticated = true
        restoreRoute(route)
    }

    fun onGuestAccess() {
        token = "guest-session"
        username = "Guest"
        roleCode = "GUEST"
        isAuthenticated = true
        currentScreen = Screen.Map
        replaceOmsRoute(Screen.Map.route())
    }

    // 🔹 Logout
    fun logout() {
        ProjectRepository.clear()
        token = null
        username = ""
        roleCode = ""
        isAuthenticated = false
        selectedProject = null
        currentScreen = Screen.Login
        replaceOmsRoute("login")
    }
}

private fun Screen.route(): String = when (this) {
    Screen.Login -> "login"
    Screen.Dashboard -> "dashboard"
    Screen.Projects -> "projects"
    Screen.CreateProject -> "project-new"
    Screen.EditProject -> "project-edit"
    Screen.ProjectDetail -> "project"
    Screen.CreateInspection -> "inspection-new"
    Screen.Map -> "map"
    Screen.Inspections -> "inspections"
    Screen.Financial -> "financial"
    Screen.Procurement -> "procurement"
    Screen.Documents -> "documents"
    Screen.Admin -> "admin"
}
