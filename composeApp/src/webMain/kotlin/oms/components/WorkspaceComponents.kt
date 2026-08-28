package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

@Composable
fun OmsBadge(text: String, color: Color, fontWeight: FontWeight = FontWeight.Medium) {
    Text(text, color = color, fontWeight = fontWeight,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color.copy(alpha = .09f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp))
}

@Composable
fun PageHeading(title: String, icon: ImageVector, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        actions()
    }
}

/** Local loading/error/empty feedback, never a silent blank region. */
@Composable
fun ContentState(message: String, loading: Boolean = false, error: Boolean = false, onRetry: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(
        if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.shapes.medium).padding(OmsDimensions.SpaceMedium),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        onRetry?.let { TextButton(onClick = it) { Text(LocalizationManager.t("retry")) } }
    }
}

/** A readable two-column analytical layout, stacking on smaller laptops. */
@Composable
fun AdaptiveChartRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 900.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(1f)) { second() }
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { first(); second() }
    }
}

@Composable
fun FormSectionTitle(text: String, icon: ImageVector) {
    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(OmsDimensions.IconSize), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
