package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.TableActionIconButton
import oms.localization.LocalizationManager

/** One workbook row per quality assessment, rather than a fragile pipe-delimited text area. */
@Composable
internal fun RepeatableQualityRemarks(
    values: List<QualityRemarkInput>,
    onChange: (List<QualityRemarkInput>) -> Unit
) {
    fun update(index: Int, transform: (QualityRemarkInput) -> QualityRemarkInput) {
        onChange(values.mapIndexed { current, item -> if (current == index) transform(item) else item })
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEachIndexed { index, remark ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FBFC))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(remark.work, { value -> update(index) { it.copy(work = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_quality_work")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                        OutlinedTextField(remark.comment, { value -> update(index) { it.copy(comment = value.inspectionText(8_000)) } }, label = { Text(LocalizationManager.t("sir_quality_comment")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                        OutlinedTextField(remark.rectification, { value -> update(index) { it.copy(rectification = value.inspectionText(8_000)) } }, label = { Text(LocalizationManager.t("sir_quality_rectification")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                        OutlinedTextField(remark.status, { value -> update(index) { it.copy(status = value.inspectionText(1_000)) } }, label = { Text(LocalizationManager.t("sir_quality_status")) }, minLines = 2, maxLines = 6, modifier = Modifier.weight(1f))
                    }
                    if (values.size > 1) {
                        TableActionIconButton(LocalizationManager.t("delete"), Icons.Default.Remove) {
                            onChange(values.filterIndexed { current, _ -> current != index })
                        }
                    }
                }
            }
        }
        TableActionIconButton(LocalizationManager.t("add"), Icons.Default.Add) {
            onChange(values + QualityRemarkInput())
        }
    }
}
