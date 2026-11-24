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
    BoxWithConstraints(
        Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.small).padding(8.dp)
    ) {
        val separateRetry = maxWidth < 420.dp && onRetry != null
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = foreground)
                else Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = foreground)
                Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall, color = foreground)
                if (onRetry != null && !separateRetry) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        OmsTooltipBox(tooltip = { Text(LocalizationManager.t("retry")) }, enabled = !loading) {
                            WorkspaceIconButton(onClick = onRetry, enabled = !loading,
                                modifier = Modifier.size(28.dp).chartControlFocus()) {
                                Icon(Icons.Outlined.Refresh, LocalizationManager.t("retry"),
                                    Modifier.size(18.dp), tint = foreground)
                            }
                        }
                    }
                }
            }
            if (onRetry != null && separateRetry) {
                WorkspaceTextButton(onClick = onRetry, enabled = !loading,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = foreground)) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(LocalizationManager.t("retry"))
                }
            }
        }
    }
}
