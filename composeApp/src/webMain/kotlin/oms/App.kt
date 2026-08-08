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

@Composable
fun App() {
    val appState = remember { AppState() }

    OMSTheme {
        SelectionContainer {
            if (!appState.isAuthenticated) {
                LoginScreen(
                    onLoginSuccess = { user ->
                        appState.onLoginSuccess(user.username, user.role.code)
                    }
                )
            } else if (appState.isAuthenticated) {
                AppLayout(appState)
            }
        }
    }
}
