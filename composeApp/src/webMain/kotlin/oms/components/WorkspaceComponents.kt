package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    val foreground = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    val iconTint = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary

    @Composable
    fun StatusIcon() {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = iconTint)
        else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, tint = iconTint, modifier = Modifier.size(22.dp))
    }

    @Composable
    fun RetryAction() {
        onRetry?.let { retry ->
            OutlinedButton(
                onClick = retry,
                enabled = !loading,
                modifier = if (loading) Modifier else Modifier.buttonHandCursor(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("retry"))
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().background(background, MaterialTheme.shapes.medium)
        .border(1.dp, if (error) MaterialTheme.colorScheme.error.copy(alpha = .2f) else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        .padding(OmsDimensions.SpaceMedium)) {
        @Composable
        fun Message(modifier: Modifier) {
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                StatusIcon()
                Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = foreground)
            }
        }
        if (maxWidth >= 440.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Message(Modifier.weight(1f))
                RetryAction()
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Message(Modifier.fillMaxWidth())
                if (onRetry != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { RetryAction() }
                }
            }
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

@Composable
fun PageArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val label = LocalizationManager.t(if (forward) "next_page" else "previous_page")
    val icon = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft
    OmsTooltipBox(tooltip = { Text(label) }) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (enabled) Modifier.buttonHandCursor() else Modifier,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** Compact navigation with direct access to the ends of a long list. */
@Composable
fun PageNavigation(currentPage: Int, pageCount: Int, summary: String, onPage: (Int) -> Unit) {
    val lastPage = (pageCount - 1).coerceAtLeast(0)
    Column(horizontalAlignment = Alignment.End) {
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val firstLabel = LocalizationManager.t("first_page")
            val lastLabel = LocalizationManager.t("last_page")
            OmsTooltipBox(tooltip = { Text(firstLabel) }) {
                IconButton(onClick = { onPage(0) }, enabled = currentPage > 0,
                    modifier = if (currentPage > 0) Modifier.buttonHandCursor() else Modifier) {
                    Icon(Icons.Default.FirstPage, contentDescription = firstLabel)
                }
            }
            PageArrow(forward = false, enabled = currentPage > 0) { onPage((currentPage - 1).coerceAtLeast(0)) }
            PageArrow(forward = true, enabled = currentPage < lastPage) { onPage((currentPage + 1).coerceAtMost(lastPage)) }
            OmsTooltipBox(tooltip = { Text(lastLabel) }) {
                IconButton(onClick = { onPage(lastPage) }, enabled = currentPage < lastPage,
                    modifier = if (currentPage < lastPage) Modifier.buttonHandCursor() else Modifier) {
                    Icon(Icons.Default.LastPage, contentDescription = lastLabel)
                }
            }
        }
    }
}
