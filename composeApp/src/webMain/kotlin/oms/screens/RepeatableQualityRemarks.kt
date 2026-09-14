package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager

/** Editable counterpart of the four-column quality table in the SIR workbook. */
@Composable
internal fun RepeatableQualityRemarks(
    values: List<QualityRemarkInput>,
    onChange: (List<QualityRemarkInput>) -> Unit
) {
    fun update(index: Int, transform: (QualityRemarkInput) -> QualityRemarkInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LocalizationManager.t("sir_quality_work"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Text(LocalizationManager.t("sir_quality_comment"), Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium)
            Text(LocalizationManager.t("sir_quality_rectification"), Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium)
            Text(LocalizationManager.t("sir_quality_status"), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(40.dp))
        }
        values.forEachIndexed { index, remark ->
            androidx.compose.runtime.LaunchedEffect(remark.photoKey, remark.work) {
                setPendingInspectionPhotoDescription(remark.photoKey, remark.work)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(remark.work, { value -> update(index) { it.copy(work = value.inspectionText(4_000)) } }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                    OutlinedTextField(remark.comment, { value -> update(index) { it.copy(comment = value.inspectionText(8_000)) } }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(remark.rectification, { value -> update(index) { it.copy(rectification = value.inspectionText(8_000)) } }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1.4f))
                    OutlinedTextField(remark.status, { value -> update(index) { it.copy(status = value.inspectionText(1_000)) } }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                    if (values.size > 1) {
                        TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) {
                            onChange(values.filterIndexed { current, _ -> current != index })
                        }
                    }
                    else Spacer(Modifier.width(40.dp))
                }
                OutlinedButton(onClick = {
                    openInspectionPhotoPicker(remark.photoKey) { count ->
                        update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                    }
                }) { Text(LocalizationManager.t("add_inspection_photos")) }
                if (remark.photoCount > 0) {
                    PendingInspectionPhotoPreviews(remark.photoKey, remark.photoRevision) { count ->
                        update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                    }
                }
            }
        }
        TableActionIconButton(LocalizationManager.t("add"), Icons.Default.Add) {
            onChange(values + QualityRemarkInput())
        }
    }
}
