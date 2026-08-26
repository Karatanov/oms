package oms.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oms.model.Project
import oms.data.ProjectRepository

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
        currentScreen = screen
    }

    // 🔹 Відкрити detail-екран конкретного проєкту
    fun openProjectDetail(project: Project) {
        selectedProject = project
        currentScreen = Screen.ProjectDetail
    }

    // 🔹 Відкрити створення інспекції
    fun openCreateInspection() {
        currentScreen = Screen.CreateInspection
    }

    fun openCreateProject() {
        currentScreen = Screen.CreateProject
    }

    fun openEditProject(project: Project) {
        selectedProject = project
        currentScreen = Screen.EditProject
    }

    // 🔹 Успішний логін
    fun onLoginSuccess(username: String, roleCode: String) {
        this.token = "session"
        this.username = username
        this.roleCode = roleCode
        isAuthenticated = true

        // 🔹 Після логіну завжди відкриваємо Dashboard
        currentScreen = Screen.Dashboard
    }

    fun onGuestAccess() {
        token = "guest-session"
        username = "Guest"
        roleCode = "GUEST"
        isAuthenticated = true
        currentScreen = Screen.Map
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
    }
}
