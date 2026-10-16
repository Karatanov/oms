package oms.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** Explains that project status tracks implementation, rather than procurement. */
@Composable
fun ProjectStatusInfoButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OmsTooltipBox(
        modifier = modifier,
        forceOpen = open,
        tooltip = {
            Column {
                Text(
                    LocalizationManager.t("project_status_info_title"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(LocalizationManager.t("project_status_info_description"), style = MaterialTheme.typography.bodySmall)
                Text(
                    "• ${LocalizationManager.t("project_status_planned")} — ${LocalizationManager.t("project_status_info_planned")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ${LocalizationManager.t("project_status_active")} — ${LocalizationManager.t("project_status_info_active")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ${LocalizationManager.t("project_status_suspended")} — ${LocalizationManager.t("project_status_info_suspended")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ${LocalizationManager.t("project_status_completed")} — ${LocalizationManager.t("project_status_info_completed")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ${LocalizationManager.t("project_status_archived")} — ${LocalizationManager.t("project_status_info_archived")}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• ${LocalizationManager.t("project_status_dlp")} — ${LocalizationManager.t("project_status_info_dlp")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    ) {
        IconButton(
            onClick = { open = !open },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = LocalizationManager.t("project_status_info_label"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A reusable Status heading with an accessible implementation-status explanation. */
@Composable
fun ProjectStatusLabelWithInfo(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(LocalizationManager.t("status"), style = style)
        Spacer(Modifier.width(2.dp))
        ProjectStatusInfoButton()
    }
}
