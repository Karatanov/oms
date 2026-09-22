package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager

internal data class PurchasedMaterialInput(
    val photoKey: String = "material-${kotlin.random.Random.nextInt()}-${kotlin.random.Random.nextInt()}",
    val materialsAndEquipment: String = "",
    val characteristics: String = "",
    // An unchecked control is an explicit "no", never an undefined value.
    val perDed: String = "no",
    val notes: String = "",
    val photoCount: Int = 0,
    val photoRevision: Int = 0
)

@Composable
internal fun RepeatablePurchasedMaterials(
    values: List<PurchasedMaterialInput>,
    onChange: (List<PurchasedMaterialInput>) -> Unit,
    attachedPhotos: List<oms.data.ApiInspectionPhoto>
) {
    fun update(index: Int, transform: (PurchasedMaterialInput) -> PurchasedMaterialInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, material ->
            val persistedPhotos = attachedPhotos.filter { it.editorAssociationKey() == material.photoKey }
            androidx.compose.runtime.LaunchedEffect(material.photoKey, material.materialsAndEquipment) {
                setPendingInspectionPhotoDescription(material.photoKey, material.materialsAndEquipment)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(material.materialsAndEquipment, { value -> update(index) { it.copy(materialsAndEquipment = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_materials_equipment")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                        OutlinedTextField(material.characteristics, { value -> update(index) { it.copy(characteristics = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_material_characteristics")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(LocalizationManager.t("sir_material_per_ded"))
                            Checkbox(
                                checked = material.perDed.equals("yes", ignoreCase = true),
                                onCheckedChange = { checked -> update(index) { it.copy(perDed = if (checked) "yes" else "no") } }
                            )
                        }
                        OutlinedTextField(material.notes, { value -> update(index) { it.copy(notes = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_material_notes")) }, minLines = 2, maxLines = 5, modifier = Modifier.weight(1f))
                        TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) { onChange(values.filterIndexed { current, _ -> current != index }) }
                    }
                    OutlinedButton(onClick = {
                        openInspectionPhotoPicker(material.photoKey) { count ->
                            update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                        }
                    }) { Text(LocalizationManager.t("add_inspection_photos")) }
                    if (material.photoCount > 0) {
                        PendingInspectionPhotoPreviews(material.photoKey, material.photoRevision) { count ->
                            update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                        }
                    }
                    if (persistedPhotos.isNotEmpty()) {
                        InspectionActivityPhotoGallery(
                            activityKey = "saved-${material.photoKey}",
                            photos = persistedPhotos,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // Materials are an open-ended report table.  Keep the add control
        // available in edit mode as well, even when imported data already
        // contains three or more rows.
        TableActionIconButton(LocalizationManager.t("sir_add_material"), Icons.Default.Add) {
            onChange(values + PurchasedMaterialInput())
        }
    }
}
