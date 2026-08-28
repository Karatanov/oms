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

val constructionTypes = listOf("reconstruction", "capital_repair", "new_construction")

@Composable
fun ConstructionTypeSelector(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(LocalizationManager.t("construction_type"))
        InlineOptionPicker(
            options = constructionTypes,
            selected = value,
            prompt = LocalizationManager.t("construction_type"),
            onSelect = onValueChange,
            itemLabel = String::constructionTypeLabel
        )
    }
}

@Composable
fun ConstructionTypeChip(value: String, fontWeight: FontWeight = FontWeight.Medium) {
    val color = when (value) {
        "reconstruction" -> Color(0xFF1565C0)
        "capital_repair" -> oms.theme.OmsColors.Warning
        "new_construction" -> oms.theme.OmsColors.Success
        else -> Color(0xFF546E7A)
    }
    OmsBadge(value.constructionTypeLabel(), color, fontWeight)
}

fun String.constructionTypeLabel(): String = when (lowercase()) {
    "reconstruction" -> LocalizationManager.t("construction_reconstruction")
    "capital_repair" -> LocalizationManager.t("construction_capital_repair")
    "new_construction" -> LocalizationManager.t("construction_new_construction")
    else -> this
}
