package oms.usif.ua.ufsi.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlin.js.JsName

@JsName("showLeafletMapPane")
external fun showLeafletMapPane()

@JsName("hideLeafletMapPane")
external fun hideLeafletMapPane()

@Composable
fun LeafletMapView() {
    DisposableEffect(Unit) {
        showLeafletMapPane()

        onDispose {
            hideLeafletMapPane()
        }
    }
}