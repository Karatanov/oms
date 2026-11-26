package oms.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.components.InlineOptionPicker
import oms.localization.LocalizationManager

private const val RECTIFICATION_IN_PROGRESS = "У процесі усунення"
private const val RECTIFICATION_RESOLVED = "Усунено"
private val rectificationStatuses = listOf(RECTIFICATION_IN_PROGRESS, RECTIFICATION_RESOLVED)

private fun rectificationStatusLabel(status: String): String = when (status) {
    RECTIFICATION_IN_PROGRESS -> LocalizationManager.t("sir_rectification_in_progress")
    RECTIFICATION_RESOLVED -> LocalizationManager.t("sir_rectification_resolved")
    else -> status
}

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
        values.forEachIndexed { index, remark ->
            // Evidence in this section is captioned from the quality comment,
            // not the work name: it is the field that describes the issue
            // depicted by the photo.
            androidx.compose.runtime.LaunchedEffect(remark.photoKey, remark.comment) {
                setPendingInspectionPhotoDescription(remark.photoKey, remark.comment)
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    oms.components.ResponsiveFormRow { fieldModifier ->
                        OutlinedTextField(remark.work, { value -> update(index) { it.copy(work = value.inspectionText(4_000)) } }, label = { Text(LocalizationManager.t("sir_quality_work")) }, minLines = 2, maxLines = 6, modifier = fieldModifier)
                        OutlinedTextField(remark.comment, { value -> update(index) { it.copy(comment = value.inspectionText(8_000)) } }, label = { Text(LocalizationManager.t("sir_quality_comment")) }, minLines = 2, maxLines = 6, modifier = fieldModifier)
                        OutlinedTextField(remark.rectification, { value -> update(index) { it.copy(rectification = value.inspectionText(8_000)) } }, label = { Text(LocalizationManager.t("sir_quality_rectification")) }, minLines = 2, maxLines = 6, modifier = fieldModifier)
                        InlineOptionPicker(
                            options = rectificationStatuses,
                            selected = remark.status.takeIf { it.isNotBlank() },
                            prompt = LocalizationManager.t("sir_quality_status"),
                            onSelect = { status -> update(index) { it.copy(status = status) } },
                            itemLabel = ::rectificationStatusLabel,
                            modifier = fieldModifier,
                            showSelectionContext = true
                        )

                    }
                    oms.components.WrappingActions(
                        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        oms.components.WorkspaceOutlinedButton(modifier = Modifier.heightIn(min = 48.dp), onClick = {
                            openInspectionPhotoPicker(remark.photoKey) { count ->
                                update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                            }
                        }) { Text(LocalizationManager.t("add_inspection_photos")) }
                        if (values.size > 1) {
                            oms.components.WorkspaceTextButton(
                                onClick = { onChange(values.filterIndexed { current, _ -> current != index }) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text(LocalizationManager.t("delete")) }
                        }
                    }
                    if (remark.photoCount > 0) {
                        PendingInspectionPhotoPreviews(remark.photoKey, remark.photoRevision) { count ->
                            update(index) { it.copy(photoCount = count, photoRevision = it.photoRevision + 1) }
                        }
                    }
                }
            }
        }
        oms.components.WorkspaceAddButton(LocalizationManager.t("add")) {
            onChange(values + QualityRemarkInput(photoKey = "quality-${values.size}"))
        }
    }
}
