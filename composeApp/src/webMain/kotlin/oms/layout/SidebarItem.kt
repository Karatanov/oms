package oms.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import oms.navigation.Screen

@Composable
fun SidebarItem(title: String, icon: ImageVector, screen: Screen, current: Screen,
    onNavigate: (Screen) -> Unit, compact: Boolean = false
) {
    val isSelected = current == screen ||
        (screen == Screen.Projects && current in listOf(Screen.CreateProject, Screen.EditProject, Screen.ProjectDetail)) ||
        (screen == Screen.Inspections && current == Screen.CreateInspection)
    // A direct button is intentional: wrapping sidebar links in a tooltip box on
    // Wasm can intercept hover state and leave the text-selection cursor visible.
    TextButton(
        onClick = { onNavigate(screen) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true).semantics { selected = isSelected },
        shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, if (compact) title else null, Modifier.size(20.dp))
        if (!compact) {
            Spacer(Modifier.width(12.dp))
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
