package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
        "pending_review" -> LocalizationManager.t("pending_review_status") to oms.theme.OmsColors.Warning
        "rejected" -> LocalizationManager.t("rejected_status") to oms.theme.OmsColors.Danger
        "completed" -> LocalizationManager.t("completed_status") to oms.theme.OmsColors.Success
        else -> status.replace('_', ' ') to Color(0xFF546E7A)
    }
    OmsBadge(label, color)
}

@Composable
fun RoleChip(role: String) {
    val color = when (role.uppercase()) { "ADMIN" -> Color(0xFF6A1B9A); "PROJECT_MANAGER" -> Color(0xFF1565C0); "INSPECTOR" -> Color(0xFF00838F); else -> Color(0xFF546E7A) }
    val label = LocalizationManager.t("role_${role.lowercase()}")
    OmsBadge(label, color, FontWeight.Medium)
}

@Composable
fun DocumentTypeChip(type: String) {
    val (label, color) = when (type.lowercase()) {
        "contract" -> LocalizationManager.t("contract") to Color(0xFF1565C0)
        "project" -> LocalizationManager.t("project") to Color(0xFF6A1B9A)
        "subproject" -> LocalizationManager.t("subproject") to Color(0xFF7B1FA2)
        "subproject_part" -> LocalizationManager.t("subproject_part") to Color(0xFFAD1457)
        "design" -> LocalizationManager.t("design") to Color(0xFF00838F)
        "estimate" -> LocalizationManager.t("estimate") to oms.theme.OmsColors.Warning
        "invoice", "act" -> LocalizationManager.t("financial_doc") to oms.theme.OmsColors.Success
        "photo" -> LocalizationManager.t("photo") to Color(0xFFE65100)
        "sir_source" -> LocalizationManager.t("source_file") to Color(0xFF5C6BC0)
        else -> LocalizationManager.t("other") to Color(0xFF546E7A)
    }
    OmsBadge(label, color, FontWeight.Medium)
}
