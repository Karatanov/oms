package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import oms.components.FilterDropdown
import oms.localization.LocalizationManager

@Composable
fun DocumentsScreen(
    canManageDocuments: Boolean = true
) {
    var searchText by remember { mutableStateOf("") }
    var projectFilter by remember { mutableStateOf<String?>(null) }
    var uploadedByFilter by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<DocumentType?>(null) }
    var sortDescending by remember { mutableStateOf(true) }

    val documents = remember { sampleDocuments() }

    val filteredDocuments = remember(searchText, projectFilter, uploadedByFilter, typeFilter, sortDescending) {
        documents
            .filter { doc ->
                (searchText.isBlank() ||
                        doc.fileName.contains(searchText, ignoreCase = true) ||
                        doc.description.contains(searchText, ignoreCase = true)
                        ) &&
                        (projectFilter == null || doc.project == projectFilter) &&
                        (uploadedByFilter == null || doc.uploadedBy == uploadedByFilter) &&
                        (typeFilter == null || doc.type == typeFilter)
            }
            .let { list ->
                if (sortDescending) list.sortedByDescending { it.date } else list.sortedBy { it.date }
            }
    }

    val projectOptions = remember { documents.map { it.project }.distinct().sorted() }
    val uploadedByOptions = remember { documents.map { it.uploadedBy }.distinct().sorted() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LocalizationManager.t("documents_title"),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = { }) {
                Icon(Icons.Default.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(LocalizationManager.t("upload_document"))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterDropdown(
                label = LocalizationManager.t("project"),
                options = projectOptions,
                selected = projectFilter,
                onSelect = { projectFilter = it }
            )

            FilterDropdown(
                label = LocalizationManager.t("uploaded_by"),
                options = uploadedByOptions,
                selected = uploadedByFilter,
                onSelect = { uploadedByFilter = it }
            )

            FilterDropdown(
                label = LocalizationManager.t("type"),
                options = DocumentType.entries,
                selected = typeFilter,
                onSelect = { typeFilter = it },
                itemLabel = { type ->
                    when (type) {
                        DocumentType.CONTRACT -> LocalizationManager.t("contract")
                        DocumentType.DESIGN -> LocalizationManager.t("design")
                        DocumentType.ESTIMATE -> LocalizationManager.t("estimate")
                        DocumentType.FINANCIAL_DOC -> LocalizationManager.t("financial_doc")
                        DocumentType.PHOTO -> LocalizationManager.t("photo")
                    }
                }
            )

            TextButton(onClick = { sortDescending = !sortDescending }) {
                Text(
                    if (sortDescending)
                        LocalizationManager.t("newest_first")
                    else
                        LocalizationManager.t("oldest_first")
                )
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text(LocalizationManager.t("search")) },
                modifier = Modifier.weight(1f)
            )
        }

        if (filteredDocuments.isEmpty()) {
            EmptyDocumentsState()
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DocumentsTableHeader()
                    HorizontalDivider()

                    filteredDocuments.forEach { document ->
                        DocumentRow(
                            document = document,
                            canManageDocuments = canManageDocuments
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDocumentsState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = LocalizationManager.t("no_documents"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentsTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            LocalizationManager.t("icon"),
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("filename"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("project"),
            modifier = Modifier.width(160.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("type"),
            modifier = Modifier.width(140.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("uploaded_by"),
            modifier = Modifier.width(160.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("date"),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("size"),
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            LocalizationManager.t("actions"),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun DocumentRow(
    document: DocumentUi,
    canManageDocuments: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocumentTypeIcon(document.type)

        Text(document.fileName, modifier = Modifier.weight(1f))
        Text(document.project, modifier = Modifier.width(160.dp))
        Text(document.type.label, modifier = Modifier.width(140.dp))
        Text(document.uploadedBy, modifier = Modifier.width(160.dp))
        Text(document.date, modifier = Modifier.width(120.dp))
        Text(document.size, modifier = Modifier.width(90.dp))

        Row(
            modifier = Modifier.width(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.Download, contentDescription = LocalizationManager.t("download"))
            }

            if (canManageDocuments) {
                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = LocalizationManager.t("delete"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    HorizontalDivider()
}

@Composable
private fun DocumentTypeIcon(type: DocumentType) {
    val background = when (type) {
        DocumentType.CONTRACT -> Color(0xFF1E88E5)
        DocumentType.DESIGN -> Color(0xFF8E24AA)
        DocumentType.ESTIMATE -> Color(0xFFF57C00)
        DocumentType.FINANCIAL_DOC -> Color(0xFF2E7D32)
        DocumentType.PHOTO -> Color(0xFF546E7A)
    }

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(32.dp)
            .background(
                color = background.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = type.shortLabel,
            color = background,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private enum class DocumentType(val label: String, val shortLabel: String) {
    CONTRACT(LocalizationManager.t("contract"), "CTR"),
    DESIGN(LocalizationManager.t("design"), "DSN"),
    ESTIMATE(LocalizationManager.t("estimate"), "EST"),
    FINANCIAL_DOC(LocalizationManager.t("financial_doc"), "FIN"),
    PHOTO(LocalizationManager.t("photo"), "IMG")
}

private data class DocumentUi(
    val fileName: String,
    val project: String,
    val type: DocumentType,
    val uploadedBy: String,
    val date: String,
    val size: String,
    val description: String
)

private fun sampleDocuments(): List<DocumentUi> = listOf(
    DocumentUi(
        "contract_kyiv_school.pdf",
        "Kyiv School",
        DocumentType.CONTRACT,
        "Olena Kovalenko",
        "2026-03-21",
        "2.4 MB",
        "Main contract"
    ),
    DocumentUi(
        "design_pack_v3.zip",
        "Lviv Hospital",
        DocumentType.DESIGN,
        "Ihor Petrenko",
        "2026-03-18",
        "18.9 MB",
        "Updated design package"
    ),
    DocumentUi(
        "estimate_march.xlsx",
        "Kyiv School",
        DocumentType.ESTIMATE,
        "Anna Shevchenko",
        "2026-03-16",
        "1.1 MB",
        "Monthly estimate"
    ),
    DocumentUi(
        "act_1042.pdf",
        "Kharkiv Road",
        DocumentType.FINANCIAL_DOC,
        "Taras Bondar",
        "2026-03-14",
        "840 KB",
        "Payment act"
    ),
    DocumentUi(
        "site_photo_01.jpg",
        "Kyiv School",
        DocumentType.PHOTO,
        "Olha Melnyk",
        "2026-03-12",
        "3.8 MB",
        "Inspection photo"
    )
)