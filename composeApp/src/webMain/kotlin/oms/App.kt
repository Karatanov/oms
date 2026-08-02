package oms

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.text.selection.SelectionContainer
import oms.layout.AppLayout
import oms.navigation.AppState
import oms.navigation.Screen
import oms.screens.LoginScreen
import oms.data.OmsApiClient
import oms.theme.OMSTheme

private const val SHOW_LOGIN_SCREEN = false

@Composable
fun App() {
    val appState = remember {
        AppState().apply {
            if (SHOW_LOGIN_SCREEN) {
                isAuthenticated = false
                token = null
                currentScreen = Screen.Login
            } else {
                isAuthenticated = false
                token = null
                currentScreen = Screen.Dashboard
            }
        }
    }
    var autoLoginError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!SHOW_LOGIN_SCREEN) {
            runCatching { OmsApiClient.login("admin", "password") }
                .onSuccess { authenticated ->
                    if (authenticated) appState.onLoginSuccess("demo-admin")
                    else autoLoginError = "Demo administrator login was rejected."
                }
                .onFailure { autoLoginError = "Could not start demo session: ${it.message ?: "unknown error"}" }
        }
    }

    OMSTheme {
        SelectionContainer {
            if (!appState.isAuthenticated && SHOW_LOGIN_SCREEN) {
                LoginScreen(
                    onLoginSuccess = {
                        appState.onLoginSuccess("mock-token")
                    }
                )
            } else if (appState.isAuthenticated) {
                AppLayout(appState)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (autoLoginError == null) CircularProgressIndicator()
                    else androidx.compose.material3.Text(autoLoginError!!)
                }
            }
        }
    }
}
