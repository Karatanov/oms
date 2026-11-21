package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** Small chart-local status; full messages remain readable at every width. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartContentState(message: String, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    val foreground = if (loading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
    val background = if (loading) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.errorContainer
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(
            Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.small).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = foreground)
            else Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = foreground)
            Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = foreground)
            if (onRetry != null) {
                OmsTooltipBox(tooltip = { Text(LocalizationManager.t("retry")) }) {
                    IconButton(onClick = onRetry, enabled = !loading,
                        modifier = Modifier.size(28.dp).then(if (!loading) Modifier.buttonHandCursor() else Modifier)) {
                        Icon(Icons.Outlined.Refresh, LocalizationManager.t("retry"), Modifier.size(18.dp), tint = foreground)
                    }
                }
            }
        }
    }
}
