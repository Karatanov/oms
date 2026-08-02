package oms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.text.selection.SelectionContainer
import oms.layout.AppLayout
import oms.navigation.AppState
import oms.navigation.Screen
import oms.screens.LoginScreen
import oms.theme.OMSTheme

private const val SHOW_LOGIN_SCREEN = true

@Composable
fun App() {
    val appState = remember {
        AppState().apply {
            if (SHOW_LOGIN_SCREEN) {
                isAuthenticated = false
                token = null
                currentScreen = Screen.Login
            } else {
                isAuthenticated = true
                token = "mock-token"
                currentScreen = Screen.Dashboard
            }
        }
    }

    OMSTheme {
        SelectionContainer {
            if (!appState.isAuthenticated) {
                LoginScreen(
                    onLoginSuccess = {
                        appState.onLoginSuccess("mock-token")
                    }
                )
            } else {
                AppLayout(appState)
            }
        }
    }
}
