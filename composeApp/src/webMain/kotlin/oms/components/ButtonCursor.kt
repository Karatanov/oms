package oms.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * A Material button contains selectable text on Web/Wasm.  Without an
 * explicit override that text can replace the button cursor with an I-beam.
 */
fun Modifier.buttonHandCursor(): Modifier =
    pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)

@Composable
fun oms.components.WorkspaceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = oms.components.WorkspaceButton(
    onClick = onClick, modifier = modifier.buttonHandCursor(), enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun oms.components.WorkspaceOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = oms.components.WorkspaceOutlinedButton(
    onClick = onClick, modifier = modifier.buttonHandCursor(), enabled = enabled, shape = shape,
    colors = colors, elevation = elevation, border = border, contentPadding = contentPadding,
    interactionSource = interactionSource, content = content
)

@Composable
fun oms.components.WorkspaceTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) = oms.components.WorkspaceTextButton(
    onClick = onClick, modifier = modifier.buttonHandCursor(), enabled = enabled, shape = shape,
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
) = androidx.compose.material3.IconButton(
    onClick = onClick, modifier = modifier.buttonHandCursor(), enabled = enabled,
    colors = colors, interactionSource = interactionSource, content = content
)
