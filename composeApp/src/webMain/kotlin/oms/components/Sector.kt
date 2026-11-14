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

private fun String.canonicalSectorKey(): String? {
    if (oms.urp3.financialSectorKey(this) == "water_supply_sewerage") return "water_supply_sewerage"
    // The approved monitoring workbooks contain both stable codes and human
    // labels.  Some rows carry one language, others a UA/EN pair and a few
    // use a descriptive phrase (for example, "Water and sewerage").  Match
    // against the complete normalized source value so a chart never exposes
    // an untranslated source label merely because its separator differs.
    val normalized = trim().lowercase()
    return when (normalized) {
    "education" -> "education"
    "health", "healthcare" -> "health"
    "housing" -> "housing"
    "water_supply" -> "water_supply"
    "sewerage" -> "sewerage"
    "heat_supply" -> "heat_supply"
    "shelter" -> "shelter"
    else -> when {
        normalized.contains("освіта") || normalized.contains("education") -> "education"
        normalized.contains("охорона здоров") || normalized.contains("healthcare") ||
            normalized.contains("health care") || normalized.contains("health") -> "health"
        normalized.contains("житло") || normalized.contains("housing") -> "housing"
        normalized.contains("водопостач") || normalized.contains("water supply") -> "water_supply"
        normalized.contains("водовідвед") || normalized.contains("каналіза") ||
            normalized.contains("sewerage") || normalized.contains("sewage") || normalized.contains("wastewater") -> "sewerage"
        normalized.contains("теплопостач") || normalized.contains("heat supply") || normalized.contains("heating") -> "heat_supply"
        normalized.contains("укрит") || normalized.contains("shelter") || normalized.contains("civil protection") -> "shelter"
        else -> null
    }
    }
}

/**
 * Approved source workbooks sometimes contain a UA/EN pair in one sector
 * cell (for example, "Водовідведення / Sewerage"). Render one locale only,
 * rather than exposing the raw source notation in charts and filters.
 */
fun String.sectorLabel(): String {
    val key = oms.urp3.financialSectorKey(this)
    return when (key) {
        "education" -> LocalizationManager.t("sector_education")
        "health", "healthcare" -> LocalizationManager.t("sector_healthcare")
        "housing" -> LocalizationManager.t("sector_housing")
        "water_supply" -> LocalizationManager.t("sector_water_supply")
        "water_supply_sewerage" -> LocalizationManager.t("sector_water_supply_sewerage")
        "sewerage" -> LocalizationManager.t("sector_sewerage")
        "heat_supply" -> LocalizationManager.t("sector_heat_supply")
        "shelter" -> LocalizationManager.t("sector_shelter")
        "security_centres" -> LocalizationManager.t("sector_security_centres")
        "Unspecified" -> LocalizationManager.t("construction_unspecified")
        else -> split(Regex("\\s*/\\s*"), limit = 2).let { values ->
            if (values.size == 2) {
                if (LocalizationManager.currentLanguage == Language.EN) values[1] else values[0]
            } else this
        }
    }
}

/** Preserve the more detailed programme directions in registry cells and filters. */
fun String.priorityDirectionLabel(): String = when (oms.urp3.priorityDirectionKey(this)) {
    "shelter_education_health" -> LocalizationManager.t("priority_shelter_education_health")
    "shelter_other_public" -> LocalizationManager.t("priority_shelter_other_public")
    else -> sectorLabel()
}

/** Stable category colours: translated captions must never change the palette. */
fun priorityDirectionColor(value: String): Color = when (oms.urp3.priorityDirectionKey(value)) {
    "education" -> Color(0xFF1565C0)
    "healthcare" -> Color(0xFF2E7D32)
    "housing" -> Color(0xFF795548)
    "water_supply" -> Color(0xFF015F96)
    "sewerage" -> Color(0xFF00695C)
    "heat_supply" -> Color(0xFF994B00)
    "shelter_education_health" -> Color(0xFF6A1B9A)
    "shelter_other_public" -> Color(0xFFAD1457)
    "security_centres" -> Color(0xFF3949AB)
    "water_supply_sewerage" -> Color(0xFF455A64)
    "shelter" -> Color(0xFF6A1B9A)
    else -> Color(0xFF546E7A)
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
        "water_supply_sewerage" -> Color(0xFF0277BD)
        "sewerage" -> Color(0xFF455A64)
        "heat_supply" -> Color(0xFFEF6C00)
        "shelter" -> Color(0xFF6A1B9A)
        else -> Color(0xFF546E7A)
    }
    OmsBadge(value.sectorLabel(), color, fontWeight)
}
