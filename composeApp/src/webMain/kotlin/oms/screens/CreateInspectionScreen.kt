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
            text = if (isEditMode) "Edit Inspection" else "Create Inspection",
            style = MaterialTheme.typography.headlineMedium
        )

        if (rejectedReason != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Text(
                    text = "Rejected: $rejectedReason",
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFF8D6E63)
                )
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = inspector,
                    onValueChange = { if (isAdmin) inspector = it },
                    label = { Text("Inspector") },
                    readOnly = !isAdmin,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Cannot be a future date") }
                )

                InspectionTypeDropdown(
                    value = inspectionType,
                    onChange = { inspectionType = it }
                )

                OutlinedTextField(
                    value = gps,
                    onValueChange = { gps = it },
                    label = { Text("GPS coordinates") },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Optional on web. Accuracy warning if > 50m.") }
                )

                OutlinedTextField(
                    value = comments,
                    onValueChange = {
                        if (it.text.length <= maxComments) comments = it
                    },
                    label = { Text("Comments") },
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

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Photos",
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
                    Text("Drag-and-drop zone / camera upload")
                }

                if (photos.isEmpty()) {
                    Text(
                        text = "No photos uploaded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Photo thumbnails go here")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onSaveDraft) {
                Text("Save Draft")
            }

            Button(onClick = onSubmit) {
                Text("Submit Report")
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

private enum class InspectionType(val label: String) {
    PLANNED("Planned"),
    UNPLANNED("Unplanned"),
    FINAL("Final")
}