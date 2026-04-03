package oms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import oms.layout.AppLayout
import oms.navigation.AppState
import oms.theme.OMSTheme

// 🔹 Точка входу в UI БЕЗ АВТОРИЗАЦІЇ
@Composable
fun App() {

    // 🔹 Глобальний стан
    val appState = remember {
        AppState().apply {
            isAuthenticated = true
            token = "mock-token"
            currentScreen = oms.navigation.Screen.Dashboard
        }
    }

    OMSTheme {

        // 🔹 Одразу відкриваємо основний layout системи
        AppLayout(appState)
    }
}
// 🔹 Точка входу в UI
//@Composable
//fun App() {
//
//    // 🔹 Глобальний стан
//    val appState = remember { AppState() }
//
//    OMSTheme {
//
//        // 🔹 Якщо користувач НЕ залогінений
//        if (!appState.isAuthenticated) {
//
//            LoginScreen(
//                onLoginSuccess = {
//                    // 🔹 Тут пізніше буде реальний API
//                    appState.onLoginSuccess("mock-token")
//                }
//            )
//
//        } else {
//
//            // 🔹 Основний layout системи (Sidebar + Content)
//            AppLayout(appState)
//        }
//    }
//}