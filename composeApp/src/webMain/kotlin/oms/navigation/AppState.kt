package oms.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import oms.model.Project

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

    // 🔹 JWT токен (поки mock)
    var token: String? by mutableStateOf(null)

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

    // 🔹 Успішний логін
    fun onLoginSuccess(token: String) {
        this.token = token
        isAuthenticated = true

        // 🔹 Після логіну завжди відкриваємо Dashboard
        currentScreen = Screen.Dashboard
    }

    // 🔹 Logout
    fun logout() {
        token = null
        isAuthenticated = false
        selectedProject = null
        currentScreen = Screen.Login
    }
}
