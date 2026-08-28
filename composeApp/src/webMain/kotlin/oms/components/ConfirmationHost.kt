package oms.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oms.localization.LocalizationManager

class DeleteConfirmation internal constructor() {
    internal var request by mutableStateOf<Pair<String, () -> Unit>?>(null)
    fun show(name: String, action: () -> Unit) { request = name to action }
}
val LocalDeleteConfirmation = staticCompositionLocalOf<DeleteConfirmation> { error("ConfirmationHost is required") }

@Composable
fun ConfirmationHost(content: @Composable () -> Unit) {
    val state = remember { DeleteConfirmation() }
    CompositionLocalProvider(LocalDeleteConfirmation provides state) {
        Box(Modifier.fillMaxSize()) {
            content()
            state.request?.let { (name, action) ->
                WasmSafeOverlay(onDismiss = { state.request = null }) {
                    Card(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                            Text(LocalizationManager.t("confirm_delete_title"), style = MaterialTheme.typography.titleLarge)
                            Text(LocalizationManager.t("confirm_delete_message").replace("{name}", name))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                                OutlinedButton(onClick = { state.request = null }) { Text(LocalizationManager.t("cancel")) }
                                Button(onClick = { state.request = null; action() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(LocalizationManager.t("delete")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
