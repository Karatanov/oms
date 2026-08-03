package oms.components

import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun ReportStatusChip(status: String) {
    val (label, color) = when (status.lowercase()) {
        "draft" -> "Draft" to Color(0xFF757575)
        "pending_review" -> "Pending review" to Color(0xFFF9A825)
        "completed" -> "Completed" to Color(0xFF2E7D32)
        else -> status.replace('_', ' ') to Color(0xFF546E7A)
    }
    AssistChip(onClick = {}, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color.White))
}

@Composable
fun RoleChip(role: String) {
    val color = when (role.uppercase()) { "ADMIN" -> Color(0xFF6A1B9A); "PROJECT_MANAGER" -> Color(0xFF1565C0); "INSPECTOR" -> Color(0xFF00838F); else -> Color(0xFF546E7A) }
    AssistChip(onClick = {}, label = { Text(role.replace('_', ' ')) }, colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color.White))
}
