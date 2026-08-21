package oms.components

import androidx.compose.foundation.background
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
    InlineOptionPicker(
        options = constructionTypes,
        selected = value,
        prompt = LocalizationManager.t("construction_type"),
        onSelect = onValueChange,
        itemLabel = String::constructionTypeLabel,
        modifier = modifier
    )
}

@Composable
fun ConstructionTypeChip(value: String, fontWeight: FontWeight = FontWeight.Medium) {
    val color = when (value) {
        "reconstruction" -> Color(0xFF1565C0)
        "capital_repair" -> Color(0xFFEF6C00)
        "new_construction" -> Color(0xFF2E7D32)
        else -> Color(0xFF546E7A)
    }
    Text(
        text = value.constructionTypeLabel(),
        color = color,
        fontWeight = fontWeight,
        modifier = Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

fun String.constructionTypeLabel(): String = when (lowercase()) {
    "reconstruction" -> LocalizationManager.t("construction_reconstruction")
    "capital_repair" -> LocalizationManager.t("construction_capital_repair")
    "new_construction" -> LocalizationManager.t("construction_new_construction")
    else -> this
}
