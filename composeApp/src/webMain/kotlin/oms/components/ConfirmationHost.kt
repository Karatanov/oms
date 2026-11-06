package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

class DeleteConfirmation internal constructor() {
    internal data class Request(
        val name: String,
        val archive: Boolean,
        val action: () -> Unit,
        val archiveAlternative: (() -> Unit)? = null,
        val permanentlyDelete: Boolean = false
    )
    internal var request by mutableStateOf<Request?>(null)
    fun show(name: String, action: () -> Unit) { request = Request(name, false, action) }
    fun showWithArchive(name: String, deleteAction: () -> Unit, archiveAction: () -> Unit) {
        request = Request(name, false, deleteAction, archiveAction, permanentlyDelete = true)
    }
    fun showArchive(name: String, action: () -> Unit) { request = Request(name, true, action) }
}
val LocalDeleteConfirmation = staticCompositionLocalOf<DeleteConfirmation> { error("ConfirmationHost is required") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmationHost(content: @Composable () -> Unit) {
    val state = remember { DeleteConfirmation() }
    CompositionLocalProvider(LocalDeleteConfirmation provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.request?.let { request ->
                WasmSafeOverlay(onDismiss = { state.request = null }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Card(
                            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            BoxWithConstraints(Modifier.fillMaxWidth().padding(20.dp)) {
                                val narrow = maxWidth < 400.dp
                                val actionColor = if (request.archive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = if (request.archive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Icon(
                                            if (request.archive) Icons.Outlined.Archive else Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = actionColor,
                                            modifier = Modifier.padding(12.dp).size(24.dp)
                                        )
                                    }
                                    Text(
                                        LocalizationManager.t(
                                            if (request.archive) "archive"
                                            else if (request.permanentlyDelete) "confirm_permanent_delete_title"
                                            else "confirm_delete_title"
                                        ),
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    if (request.archive && request.name.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerLow
                                        ) {
                                            Text(request.name, Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                    Text(
                                        if (request.archive) LocalizationManager.t("archive_confirmation")
                                        else LocalizationManager.t(
                                            if (request.permanentlyDelete) "confirm_permanent_delete_message"
                                            else "confirm_delete_message"
                                        ).replace("{name}", request.name),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    @Composable
                                    fun Actions(modifier: Modifier) {
                                        OutlinedButton(onClick = { state.request = null }, modifier = modifier) {
                                            Text(LocalizationManager.t("cancel"))
                                        }
                                        request.archiveAlternative?.let { archiveAction ->
                                            OutlinedButton(
                                                onClick = {
                                                    state.request = null
                                                    archiveAction()
                                                },
                                                modifier = modifier
                                            ) {
                                                Text(LocalizationManager.t("archive"))
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                state.request = null
                                                request.action()
                                            },
                                            modifier = modifier,
                                            colors = ButtonDefaults.buttonColors(containerColor = actionColor)
                                        ) {
                                            Text(LocalizationManager.t(
                                                if (request.archive) "archive"
                                                else if (request.permanentlyDelete) "permanently_delete"
                                                else "delete"
                                            ))
                                        }
                                    }
                                    if (narrow) {
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Actions(Modifier.fillMaxWidth())
                                        }
                                    } else {
                                        FlowRow(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Actions(Modifier)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
