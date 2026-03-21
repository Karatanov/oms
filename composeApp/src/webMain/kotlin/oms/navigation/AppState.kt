package oms.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 🔹 Центральний стан усього додатку
// 🔹 Зберігає:
//    - поточний екран
//    - авторизацію
//    - токен
class AppState {

    // 🔹 Поточний екран
    var currentScreen by mutableStateOf<Screen>(Screen.Login)

    // 🔹 Чи авторизований користувач
    var isAuthenticated by mutableStateOf(false)

    // 🔹 JWT токен (поки mock)
    var token: String? by mutableStateOf(null)

    // ---------------- NAVIGATION ----------------

    // 🔹 Перехід між екранами
    fun navigate(screen: Screen) {
        currentScreen = screen
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
        currentScreen = Screen.Login
    }
}