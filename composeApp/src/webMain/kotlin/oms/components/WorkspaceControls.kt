package oms.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared workspace controls: restrained corners, compact padding, no elevation,
 * theme-based selection colors and consistent pointer behaviour on every page.
 */
@Composable
fun WorkspaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier.then(if (enabled) Modifier.buttonHandCursor() else Modifier), enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun WorkspaceOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier.then(if (enabled) Modifier.buttonHandCursor() else Modifier), enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun WorkspaceTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = androidx.compose.material3.TextButton(
    onClick = onClick, modifier = modifier.then(if (enabled) Modifier.buttonHandCursor() else Modifier), enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun WorkspaceIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) = androidx.compose.material3.IconButton(
    onClick = onClick, modifier = modifier.then(if (enabled) Modifier.buttonHandCursor() else Modifier), enabled = enabled,
    colors = colors, interactionSource = interactionSource, content = content
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable (() -> Unit))? = null,
    trailingIcon: (@Composable (() -> Unit))? = null,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    ),
    elevation: SelectableChipElevation? = null,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null
) = androidx.compose.runtime.CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    androidx.compose.material3.FilterChip(
    selected = selected, onClick = onClick, label = { ProvideTextStyle(MaterialTheme.typography.labelMedium) { label() } }, modifier = modifier.then(if (enabled) Modifier.buttonHandCursor() else Modifier), enabled = enabled,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape, colors = colors,
    elevation = elevation, border = border, interactionSource = interactionSource
)
}
