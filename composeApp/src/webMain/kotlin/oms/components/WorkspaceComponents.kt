package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.unit.sp
import oms.localization.LocalizationManager
import oms.theme.OmsDimensions

@Composable
fun OmsBadge(text: String, color: Color, fontWeight: FontWeight = FontWeight.Medium) {
    Text(text, color = color, fontWeight = fontWeight,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.background(color.copy(alpha = .09f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageHeading(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    iconText: String? = null,
    actions: @Composable () -> Unit = {}
) {
    BoxWithConstraints(Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
        .padding(if (oms.layout.LocalMobileLayout.current) 16.dp else 20.dp)) {
        @Composable fun HeadingText(textModifier: Modifier = Modifier) {
            Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                if (iconText == null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                else Text(
                    iconText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(textModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = if (oms.layout.LocalMobileLayout.current) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (maxWidth >= 620.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HeadingText(Modifier.weight(1f))
                actions()
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { HeadingText(Modifier.weight(1f)) }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
            }
        }
    }
}

/** Local loading/error/empty feedback, never a silent blank region. */
@Composable
fun ContentState(message: String, loading: Boolean = false, error: Boolean = false, onRetry: (() -> Unit)? = null) {
    val background = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val iconTint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.medium)
        .border(1.dp, if (error) MaterialTheme.colorScheme.error.copy(alpha = .2f) else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        .padding(OmsDimensions.SpaceMedium)) {
        if (maxWidth >= 440.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = iconTint)
            else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, tint = iconTint)
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            onRetry?.let { TextButton(onClick = it) { Text(LocalizationManager.t("retry")) } }
        } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = iconTint)
                else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, tint = iconTint)
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
            onRetry?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = it) { Text(LocalizationManager.t("retry")) } } }
        }
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

/** Keep page-size and previous/next controls together, wrapping between groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptivePagination(sizeControls: @Composable () -> Unit, navigation: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { sizeControls() }
        Row(verticalAlignment = Alignment.CenterVertically) { navigation() }
    }
}

@Composable
fun FormSectionTitle(text: String, icon: ImageVector) {
    HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(OmsDimensions.IconSize), tint = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
