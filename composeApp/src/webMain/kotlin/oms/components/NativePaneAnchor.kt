package oms.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.js.JsName

@JsName("setOmsPaneBounds")
private external fun setOmsPaneBounds(id: String, x: Float, y: Float, width: Float, height: Float)
@JsName("setOmsCanvasOverlay")
internal external fun setOmsCanvasOverlay(active: Boolean)

/** Keeps browser-native evidence/map panes within their Compose layout slot. */
@Composable
fun NativePaneAnchor(id: String, modifier: Modifier) {
    val density = LocalDensity.current.density
    Box(modifier.onGloballyPositioned {
        val rect = it.boundsInRoot()
        setOmsPaneBounds(id, rect.left / density, rect.top / density, rect.width / density, rect.height / density)
    })
}
