package oms

import androidx.compose.runtime.*
import androidx.compose.foundation.text.selection.SelectionContainer
import oms.layout.AppLayout
import oms.navigation.AppState
import oms.screens.LoginScreen
import oms.screens.ActivationScreen
import oms.data.OmsApiClient
import oms.theme.OMSTheme
import kotlinx.browser.window
import kotlin.js.JsName

@JsName("setOmsRouteHandler") private external fun setOmsRouteHandler(handler: (String) -> Unit)
@JsName("finishOmsStartup") private external fun finishOmsStartup()
@JsName("reportOmsStartupProgress") private external fun reportOmsStartupProgress(value: Int, messageKey: String)

@Composable
fun App() {
    val appState = remember { AppState() }
    val activationToken = remember { window.location.search.removePrefix("?").split("&").firstOrNull { it.startsWith("token=") }?.removePrefix("token=") }
    DisposableEffect(appState) {
        setOmsRouteHandler { route -> if (appState.isAuthenticated) appState.restoreRoute(route) }
        onDispose { setOmsRouteHandler { } }
    }
    LaunchedEffect(Unit) {
        // The login form must not wait for a possibly slow session endpoint.
        // Compose the usable shell first; a valid remembered session may then
        // promote it to the workspace in the background.
        withFrameNanos { }
        withFrameNanos { }
        reportOmsStartupProgress(97, "interface")
        finishOmsStartup()
        runCatching { OmsApiClient.currentSessionUser() }
            .getOrNull()
            ?.takeIf { !appState.isAuthenticated }
            ?.let { user ->
                appState.restoreAuthenticatedSession(user.username, user.role.code, window.location.hash.removePrefix("#"))
            }
    }

    OMSTheme {
        oms.components.TextContextMenuHost {
        oms.components.TooltipOverlayHost {
        oms.components.OptionOverlayHost {
        oms.components.ConfirmationHost {
        oms.components.TableScrollOverlayHost {
        // Keep the canvas neutral: a hand icon is assigned only to an actual
        // button or an explicitly clickable chart bar.  This preserves the
        // normal arrow over read-only text, rows and data cells.
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
        }
    }
    }
    }
}
