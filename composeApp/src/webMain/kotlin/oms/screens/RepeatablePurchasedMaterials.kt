package oms.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager

internal data class PurchasedMaterialInput(
    val materialsAndEquipment: String = "",
    val characteristics: String = "",
    val perDed: String = "",
    val notes: String = ""
)

@Composable
internal fun RepeatablePurchasedMaterials(
    values: List<PurchasedMaterialInput>,
    onChange: (List<PurchasedMaterialInput>) -> Unit
) {
    fun update(index: Int, transform: (PurchasedMaterialInput) -> PurchasedMaterialInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, material ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(material.materialsAndEquipment, { value -> update(index) { it.copy(materialsAndEquipment = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_materials_equipment")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                    OutlinedTextField(material.characteristics, { value -> update(index) { it.copy(characteristics = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_material_characteristics")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                    OutlinedTextField(material.perDed, { value -> update(index) { it.copy(perDed = value.inspectionText(100)) } }, label = { Text(LocalizationManager.t("sir_material_per_ded")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                    OutlinedTextField(material.notes, { value -> update(index) { it.copy(notes = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_material_notes")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                    if (values.size > 1) TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
                }
            }
        }
        if (values.size < 3) TableActionIconButton(LocalizationManager.t("sir_add_material"), Icons.Default.Add) { onChange(values + PurchasedMaterialInput()) }
    }
}
