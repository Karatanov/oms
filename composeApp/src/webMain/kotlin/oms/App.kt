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
import kotlin.js.JsName

@JsName("setOmsRouteHandler") private external fun setOmsRouteHandler(handler: (String) -> Unit)

@Composable
fun App() {
    val appState = remember { AppState() }
    var restoringSession by remember { mutableStateOf(true) }
    val activationToken = remember { window.location.search.removePrefix("?").split("&").firstOrNull { it.startsWith("token=") }?.removePrefix("token=") }
    DisposableEffect(appState) {
        setOmsRouteHandler { route -> if (appState.isAuthenticated) appState.restoreRoute(route) }
        onDispose { setOmsRouteHandler { } }
    }
    LaunchedEffect(Unit) {
        runCatching { OmsApiClient.currentSessionUser() }
            .getOrNull()
            ?.let { user -> appState.restoreAuthenticatedSession(user.username, user.role.code, window.location.hash.removePrefix("#")) }
        restoringSession = false
    }

    OMSTheme {
        oms.components.TooltipOverlayHost {
        oms.components.OptionOverlayHost {
        oms.components.ConfirmationHost {
        oms.components.TableScrollOverlayHost {
        // Keep the canvas neutral: a hand icon is assigned only to an actual
        // button or an explicitly clickable chart bar.  This preserves the
        // normal arrow over read-only text, rows and data cells.
        SelectionContainer {
            if (restoringSession) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (!appState.isAuthenticated && !activationToken.isNullOrBlank()) {
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
        }
    }
    }
}
