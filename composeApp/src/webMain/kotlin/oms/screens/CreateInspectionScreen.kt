package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

@Composable
fun CreateInspectionScreen(
    isEditMode: Boolean = false,
    currentUserName: String = "Current User",
    isAdmin: Boolean = false,
    rejectedReason: String? = null,
    onSaveDraft: () -> Unit = {},
    onSubmit: () -> Unit = {}
) {
    var inspector by remember { mutableStateOf(currentUserName) }
    var date by remember { mutableStateOf("2026-03-24") }
    var inspectionType by remember { mutableStateOf(InspectionType.PLANNED) }
    var gps by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf(TextFieldValue("")) }
    var photos by remember { mutableStateOf(listOf<String>()) }

    val maxComments = 2000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isEditMode) LocalizationManager.t("edit_inspection") else LocalizationManager.t("create_inspection"),
            style = MaterialTheme.typography.headlineMedium
        )

        if (rejectedReason != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${LocalizationManager.t("rejected")}: $rejectedReason",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF8D6E63)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = inspector,
                    onValueChange = { if (isAdmin) inspector = it },
                    label = { Text(LocalizationManager.t("inspector_label")) },
                    readOnly = !isAdmin,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(LocalizationManager.t("date_label")) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(LocalizationManager.t("cannot_be_future_date")) }
                )

                InspectionTypeDropdown(
                    value = inspectionType,
                    onChange = { inspectionType = it }
                )

                OutlinedTextField(
                    value = gps,
                    onValueChange = { gps = it },
                    label = { Text(LocalizationManager.t("gps_coordinates")) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(LocalizationManager.t("optional_on_web_accuracy_warning")) }
                )

                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        if (it.text.length <= maxComments) comments = it
                    },
                    label = { Text(LocalizationManager.t("comments")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    minLines = 5,
                    maxLines = 8,
                    supportingText = {
                        Text("${comments.text.length} / $maxComments")
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = LocalizationManager.t("photos"),
                    style = MaterialTheme.typography.titleMedium
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(LocalizationManager.t("drag_and_drop_zone"))
                }

                if (photos.isEmpty()) {
                    Text(
                        text = LocalizationManager.t("no_photos_uploaded_yet"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(LocalizationManager.t("photo_thumbnails_go_here"))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onSaveDraft) {
                Text(LocalizationManager.t("save_draft"))
            }

            Button(onClick = onSubmit) {
                Text(LocalizationManager.t("submit_report"))
            }
        }
    }
}

@Composable
private fun InspectionTypeDropdown(
    value: InspectionType,
    onChange: (InspectionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(value.label)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            InspectionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onChange(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

private enum class InspectionType {
    PLANNED,
    UNPLANNED,
    FINAL;

    val label: String
        get() = when (this) {
            PLANNED -> LocalizationManager.t("planned")
            UNPLANNED -> LocalizationManager.t("unplanned")
            FINAL -> LocalizationManager.t("final")
        }
}