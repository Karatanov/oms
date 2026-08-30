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

/** Canonical Phase 1 sectors aligned with the URP III monitoring source. */
val sectors = listOf("education", "health", "housing", "water_supply", "sewerage", "heat_supply", "shelter")

fun String.sectorLabel(): String = when (lowercase()) {
    "education" -> LocalizationManager.t("sector_education")
    "health", "healthcare" -> LocalizationManager.t("sector_healthcare")
    "housing" -> LocalizationManager.t("sector_housing")
    "water_supply" -> LocalizationManager.t("sector_water_supply")
    "sewerage" -> LocalizationManager.t("sector_sewerage")
    "heat_supply" -> LocalizationManager.t("sector_heat_supply")
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
        "health", "healthcare" -> oms.theme.OmsColors.Success
        "housing" -> Color(0xFF795548)
        "water_supply" -> Color(0xFF0277BD)
        "sewerage" -> Color(0xFF455A64)
        "heat_supply" -> Color(0xFFEF6C00)
        "shelter" -> Color(0xFF6A1B9A)
        else -> Color(0xFF546E7A)
    }
    OmsBadge(value.sectorLabel(), color, fontWeight)
}
