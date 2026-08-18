package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Page-level dialog replacement that does not use the unstable Wasm browser popup layer. */
@Composable
fun WasmSafeOverlay(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}
