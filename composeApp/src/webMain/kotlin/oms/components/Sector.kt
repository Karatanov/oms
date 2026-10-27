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
import oms.localization.Language
import oms.localization.LocalizationManager

/** Canonical Phase 1 sectors aligned with the UR III monitoring source. */
val sectors = listOf("education", "health", "housing", "water_supply", "sewerage", "heat_supply", "shelter")

private fun String.canonicalSectorKey(): String? = when (lowercase()) {
    "education" -> "education"
    "health", "healthcare" -> "health"
    "housing" -> "housing"
    "water_supply" -> "water_supply"
    "sewerage" -> "sewerage"
    "heat_supply" -> "heat_supply"
    "shelter" -> "shelter"
    else -> when {
        contains("освіта") || contains("education") -> "education"
        contains("охорона здоров") || contains("healthcare") || contains("health care") -> "health"
        contains("житло") || contains("housing") -> "housing"
        contains("водопостач") || contains("water supply") -> "water_supply"
        contains("водовідвед") || contains("sewerage") -> "sewerage"
        contains("теплопостач") || contains("heat supply") -> "heat_supply"
        contains("укрит") || contains("shelter") -> "shelter"
        else -> null
    }
}

/**
 * Approved source workbooks sometimes contain a UA/EN pair in one sector
 * cell (for example, "Водовідведення / Sewerage"). Render one locale only,
 * rather than exposing the raw source notation in charts and filters.
 */
fun String.sectorLabel(): String {
    val key = canonicalSectorKey()
    return when (key) {
        "education" -> LocalizationManager.t("sector_education")
        "health" -> LocalizationManager.t("sector_healthcare")
        "housing" -> LocalizationManager.t("sector_housing")
        "water_supply" -> LocalizationManager.t("sector_water_supply")
        "sewerage" -> LocalizationManager.t("sector_sewerage")
        "heat_supply" -> LocalizationManager.t("sector_heat_supply")
        "shelter" -> LocalizationManager.t("sector_shelter")
        else -> split(Regex("\\s*/\\s*"), limit = 2).let { values ->
            if (values.size == 2) {
                if (LocalizationManager.currentLanguage == Language.EN) values[1] else values[0]
            } else this
        }
    }
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
    val color = when (value.canonicalSectorKey()) {
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
