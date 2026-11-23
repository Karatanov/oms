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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
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
    actions: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        .padding(12.dp)) {
        // FlowRow introduces another layout scope, so retain this constraint
        // before composing the responsive action area.
        val availableWidth = maxWidth
        @Composable fun HeadingText(textModifier: Modifier = Modifier) {
            Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                if (iconText == null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                else Text(
                    iconText,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(textModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var titleTruncated by remember(title) { mutableStateOf(false) }
                OmsTooltipBox(
                    tooltip = { Text(title) },
                    enabled = titleTruncated,
                    openOnPress = true
                ) {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() }
                            .then(if (titleTruncated) Modifier.buttonHandCursor() else Modifier),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { titleTruncated = it.hasVisualOverflow }
                    )
                }
                subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (availableWidth >= 620.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeadingText(Modifier.weight(1f))
                actions?.let { content ->
                    FlowRow(
                        Modifier.widthIn(max = minOf(520.dp, availableWidth * .5f)),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) { content() }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { HeadingText(Modifier.weight(1f)) }
                actions?.let { content ->
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) { content() }
                }
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
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = iconTint)
        else Icon(if (error) Icons.Outlined.ErrorOutline else Icons.Outlined.Info, null, tint = iconTint, modifier = Modifier.size(18.dp))
    }

    @Composable
    fun RetryAction() {
        onRetry?.let { retry ->
            oms.components.WorkspaceOutlinedButton(
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
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                StatusIcon()
                Text(message, Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = foreground)
            }
        }
        if (maxWidth >= 440.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Message(Modifier.weight(1f))
                RetryAction()
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Message(Modifier.fillMaxWidth())
                if (onRetry != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { RetryAction() }
                }
            }
        }
    }
}

/** Compact chart pairs keep at least 376 dp per card, stacking on narrow screens. */
@Composable
fun AdaptiveChartRow(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.weight(1f)) { first() }
            Box(Modifier.weight(1f)) { second() }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            first()
            second()
        }
    }
}

/** Keep page-size and previous/next controls together, wrapping between groups. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptivePagination(sizeControls: @Composable () -> Unit, navigation: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { sizeControls() }
        Row(verticalAlignment = Alignment.CenterVertically) { navigation() }
    }
}

@Composable
fun FormSectionTitle(text: String, icon: ImageVector) {
    HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
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

/** Shared compact pagination control, including a visible keyboard focus ring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageNavigationButton(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OmsTooltipBox(tooltip = { Text(label) }, enabled = enabled) {
            WorkspaceIconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
                    .chartControlFocus(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun PageArrow(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    PageNavigationButton(
        label = LocalizationManager.t(if (forward) "next_page" else "previous_page"),
        icon = if (forward) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
        enabled = enabled,
        onClick = onClick
    )
}

/** Summary and navigation share a line when space permits; arrows stay together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageNavigation(currentPage: Int, pageCount: Int, summary: String, onPage: (Int) -> Unit) {
    val lastPage = (pageCount - 1).coerceAtLeast(0)
    FlowRow(
        modifier = Modifier.widthIn(max = 440.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(summary, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            PageNavigationButton(LocalizationManager.t("first_page"), Icons.Default.FirstPage, currentPage > 0) { onPage(0) }
            PageArrow(forward = false, enabled = currentPage > 0) { onPage((currentPage - 1).coerceAtLeast(0)) }
            PageArrow(forward = true, enabled = currentPage < lastPage) { onPage((currentPage + 1).coerceAtMost(lastPage)) }
            PageNavigationButton(LocalizationManager.t("last_page"), Icons.Default.LastPage, currentPage < lastPage) { onPage(lastPage) }
        }
    }
}
