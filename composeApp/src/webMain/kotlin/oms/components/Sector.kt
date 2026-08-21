package oms.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

/** The limited Phase 1 sector classification used for project filtering. */
val sectors = listOf("Education", "Healthcare", "Shelter")

fun String.sectorLabel(): String = when (lowercase()) {
    "education" -> LocalizationManager.t("sector_education")
    "healthcare" -> LocalizationManager.t("sector_healthcare")
    "shelter" -> LocalizationManager.t("sector_shelter")
    else -> this
}

@Composable
fun SectorSelector(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(LocalizationManager.t("sector_required"))
        InlineOptionPicker(
            options = sectors,
            selected = value.takeIf { it in sectors },
            prompt = LocalizationManager.t("sector_required"),
            onSelect = onValueChange,
            itemLabel = String::sectorLabel
        )
    }
}

@Composable
fun SectorChip(value: String, fontWeight: FontWeight = FontWeight.Medium) {
    val color = when (value.lowercase()) {
        "education" -> Color(0xFF1565C0)
        "healthcare" -> Color(0xFF00897B)
        "shelter" -> Color(0xFF6A1B9A)
        else -> Color(0xFF546E7A)
    }
    Text(
        text = value.sectorLabel(),
        color = color,
        fontWeight = fontWeight,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
