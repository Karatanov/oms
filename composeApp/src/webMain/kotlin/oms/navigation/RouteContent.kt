package oms.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import oms.localization.Language
import oms.localization.LocalizationManager
import oms.model.Project
import kotlin.js.JsName

@JsName("setOmsRouteTransition")
private external fun setOmsRouteTransition(busy: Boolean, indicator: Boolean, failed: Boolean, english: Boolean,
    requested: String, displayed: String, retry: () -> Unit)

/** Immutable parameters: preparing B must not change the still mounted screen A. */
data class RouteContent(
    val screen: Screen,
    val project: Project?,
    val region: String?,
    val financialSubproject: String?,
    val procurementStatus: String?,
    val inspectionUuid: String?,
    val viewingInspection: Boolean
) {
    companion object {
        fun capture(state: AppState) = RouteContent(state.currentScreen, state.selectedProject,
            state.requestedProjectRegion, state.requestedFinancialSubprojectUuid,
            state.requestedProcurementStatus, state.editingInspectionUuid, state.viewingInspection)
    }
}

private class RouteEntry(val route: RouteContent) {
    var outcome by mutableStateOf<Boolean?>(null)
}

private val LocalRouteCompletion = compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalRouteVisible = staticCompositionLocalOf { true }
val LocalRouteInteractive = staticCompositionLocalOf { true }

/** Only the screen's essential initial data participates, never widget/photo requests. */
@Composable
fun ReportRouteReadiness(loading: Boolean, failed: Boolean = false) {
    val complete = LocalRouteCompletion.current
    SideEffect { if (!loading) complete(!failed) }
}

/** Keeps the last successful composition alive until its replacement is ready. */
@Composable
fun RouteContentHost(request: RouteContent, revision: Int, content: @Composable (RouteContent) -> Unit) {
    var retry by remember(revision) { mutableStateOf(0) }
    val candidate = remember(revision, retry) { RouteEntry(request) }
    var displayed by remember { mutableStateOf<RouteEntry?>(null) }
    val transitioning = displayed !== candidate
    val failed = transitioning && candidate.outcome == false
    var showIndicator by remember(candidate) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val english = LocalizationManager.currentLanguage == Language.EN
    LaunchedEffect(candidate) {
        focus.clearFocus(force = true)
        // This delay only suppresses spinner flicker. It never declares readiness.
        delay(180)
        showIndicator = true
    }
    SideEffect {
        if (candidate.outcome == true) displayed = candidate
        setOmsRouteTransition(transitioning, showIndicator, failed,
            english, candidate.route.screen.title, displayed?.route?.screen?.title.orEmpty()) { retry++ }
    }
    DisposableEffect(Unit) {
        onDispose { setOmsRouteTransition(false, false, false, false, "", "") {} }
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        // A bounded native overlay also covers Leaflet/photo DOM surfaces above the canvas.
        oms.components.NativePaneAnchor("oms-route-transition", Modifier.fillMaxSize())
        // The key and parent stay identical on promotion, preserving forms and scroll.
        listOfNotNull(displayed, candidate.takeIf { it !== displayed }).forEach { entry ->
            key(entry) {
                val visible = entry === displayed
                val interactive = visible && !transitioning
                val complete: (Boolean) -> Unit = remember(entry) {
                    { success -> if (entry.outcome == null) entry.outcome = success }
                }
                CompositionLocalProvider(
                    LocalRouteVisible provides visible,
                    LocalRouteInteractive provides interactive,
                    LocalRouteCompletion provides complete
                ) {
                    Box(Modifier.fillMaxSize()
                        .graphicsLayer { alpha = if (visible) 1f else 0f }
                        .then(if (interactive) Modifier else Modifier.clearAndSetSemantics { })
                        .focusProperties { canFocus = interactive }
                        .onPreviewKeyEvent { !interactive }
                        .pointerInput(interactive) {
                            if (!interactive) awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }) { content(entry.route) }
                }
            }
        }
    }
}
