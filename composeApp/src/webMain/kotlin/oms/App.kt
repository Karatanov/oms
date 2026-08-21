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
import oms.screens.ActivationScreen
import oms.data.OmsApiClient
import oms.theme.OMSTheme
import kotlinx.browser.window

@Composable
fun App() {
    val appState = remember { AppState() }
    val activationToken = remember { window.location.search.removePrefix("?").split("&").firstOrNull { it.startsWith("token=") }?.removePrefix("token=") }

    OMSTheme {
        SelectionContainer {
            if (!appState.isAuthenticated && !activationToken.isNullOrBlank()) {
                ActivationScreen(activationToken) { window.location.href = window.location.pathname }
            } else if (!appState.isAuthenticated) {
                LoginScreen(
                    onLoginSuccess = { user ->
                        appState.onLoginSuccess(user.username, user.role.code)
                    },
                    onGuestAccess = { appState.onGuestAccess() }
                )
            } else if (appState.isAuthenticated) {
                AppLayout(appState)
            }
        }
    }
}
