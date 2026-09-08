package oms.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp

private data class ActiveTableNavigator(val id: Any, val scroll: ScrollState)

internal class TableScrollOverlayState {
    private var active by mutableStateOf<ActiveTableNavigator?>(null)

    fun show(id: Any, scroll: ScrollState) {
        active = ActiveTableNavigator(id, scroll)
    }

    fun dismiss(id: Any) {
        if (active?.id === id) active = null
    }

    @Composable
    fun BoxScope.Content() {
        val navigator = active ?: return
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp)
                .zIndex(100f)
                .shadow(6.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            TableScrollControls(navigator.scroll, showWhenStationary = true)
        }
    }
}

internal val LocalTableScrollOverlay = staticCompositionLocalOf<TableScrollOverlayState> {
    error("TableScrollOverlayHost is required")
}

/** Places the current table's horizontal navigator above page scroll clipping. */
@Composable
fun TableScrollOverlayHost(content: @Composable () -> Unit) {
    val state = androidx.compose.runtime.remember { TableScrollOverlayState() }
    CompositionLocalProvider(LocalTableScrollOverlay provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            with(state) { Content() }
        }
    }
}
