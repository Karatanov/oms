package oms.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import oms.components.buttonHandCursor
import androidx.compose.ui.unit.dp

/**
 * Compose selection gives the Text inside a Material control a text cursor on
 * Web/Wasm.  These same-package wrappers make every normal screen control
 * explicitly override descendants with the standard hand cursor.
 */
@Composable
fun Button(
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
) = oms.components.WorkspaceButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun OutlinedButton(
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
) = oms.components.WorkspaceOutlinedButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun TextButton(
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
) = oms.components.WorkspaceTextButton(
    onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) = oms.components.WorkspaceIconButton(
    onClick = onClick, modifier = modifier, enabled = enabled,
    colors = colors, interactionSource = interactionSource, content = content
)

@Composable
fun FilterChip(
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
) = oms.components.WorkspaceFilterChip(
    selected = selected, onClick = onClick, label = label, modifier = modifier, enabled = enabled,
    leadingIcon = leadingIcon, trailingIcon = trailingIcon, shape = shape, colors = colors,
    elevation = elevation, border = border, interactionSource = interactionSource
)
