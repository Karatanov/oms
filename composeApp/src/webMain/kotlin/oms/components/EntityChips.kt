package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

@Composable
fun ReportStatusChip(status: String) {
    val (label, color) = when (status.lowercase()) {
        "draft" -> LocalizationManager.t("draft_status") to Color(0xFF757575)
        "pending_review" -> LocalizationManager.t("pending_review_status") to Color(0xFFF9A825)
        "completed" -> LocalizationManager.t("completed_status") to Color(0xFF2E7D32)
        else -> status.replace('_', ' ') to Color(0xFF546E7A)
    }
    Box(
        modifier = Modifier
            .width(120.dp)
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun RoleChip(role: String) {
    val color = when (role.uppercase()) { "ADMIN" -> Color(0xFF6A1B9A); "PROJECT_MANAGER" -> Color(0xFF1565C0); "INSPECTOR" -> Color(0xFF00838F); else -> Color(0xFF546E7A) }
    val label = when (role.uppercase()) { "ADMIN" -> LocalizationManager.t("role_admin"); "PROJECT_MANAGER" -> LocalizationManager.t("role_project_manager"); "INSPECTOR" -> LocalizationManager.t("role_inspector"); else -> role.replace('_', ' ') }
    AssistChip(onClick = {}, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color.White))
}

@Composable
fun DocumentTypeChip(type: String) {
    val (label, color) = when (type.lowercase()) {
        "contract" -> LocalizationManager.t("contract") to Color(0xFF1565C0)
        "project" -> LocalizationManager.t("project") to Color(0xFF6A1B9A)
        "subproject" -> LocalizationManager.t("subproject") to Color(0xFF7B1FA2)
        "subproject_part" -> LocalizationManager.t("subproject_part") to Color(0xFFAD1457)
        "design" -> LocalizationManager.t("design") to Color(0xFF00838F)
        "estimate" -> LocalizationManager.t("estimate") to Color(0xFFF9A825)
        "invoice", "act" -> LocalizationManager.t("financial_doc") to Color(0xFF2E7D32)
        "photo" -> LocalizationManager.t("photo") to Color(0xFFE65100)
        else -> LocalizationManager.t("other") to Color(0xFF546E7A)
    }
    AssistChip(onClick = {}, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = Color.White))
}
