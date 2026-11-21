package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import oms.navigation.Screen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SidebarItem(title: String, icon: ImageVector, screen: Screen, current: Screen,
    onNavigate: (Screen) -> Unit, compact: Boolean = false, iconText: String? = null
) {
    val isSelected = current == screen ||
        (screen == Screen.Projects && current in listOf(Screen.CreateProject, Screen.EditProject, Screen.ProjectDetail)) ||
        (screen == Screen.Inspections && current == Screen.CreateInspection)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val visibilityRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(current, compact, title) {
        if (isSelected) {
            // Let the expanded/translated label finish layout before revealing it.
            withFrameNanos { }
            visibilityRequester.bringIntoView()
        }
    }
    // A direct button is intentional: wrapping sidebar links in a tooltip box on
    // Wasm can intercept hover state and leave the text-selection cursor visible.
    TextButton(
        onClick = { onNavigate(screen) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .bringIntoViewRequester(visibilityRequester)
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true).semantics {
                selected = isSelected
                if (compact) contentDescription = title
                testTag = "sidebar-${screen.title.lowercase().replace(' ', '-')}"
            },
        interactionSource = interactionSource,
        border = when {
            focused -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .24f))
            else -> null
        },
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        val navigationIconColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        if (iconText == null) {
            Icon(icon, null, Modifier.size(20.dp), tint = navigationIconColor)
        } else {
            Text(
                iconText,
                modifier = Modifier.width(22.dp),
                color = navigationIconColor,
                textAlign = TextAlign.Center,
                fontSize = 25.sp,
                lineHeight = 25.sp
            )
        }
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Start,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}
