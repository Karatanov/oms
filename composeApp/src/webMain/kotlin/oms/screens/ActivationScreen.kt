package oms.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import oms.data.OmsApiClient
import oms.localization.LocalizationManager
import oms.components.ContentState
import oms.components.LanguageSwitcher
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*

@Composable
fun ActivationScreen(token: String, onActivated: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LanguageSwitcher()
        Text(LocalizationManager.t("activate_title"), style = MaterialTheme.typography.headlineMedium)
        Text(LocalizationManager.t("password_guidance"))
        OutlinedTextField(password, { password = it; error = null }, label = { Text(LocalizationManager.t("password")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(confirmation, { confirmation = it; error = null }, label = { Text(LocalizationManager.t("confirm_password")) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            if (password != confirmation) { error = LocalizationManager.t("password_mismatch") } else scope.launch {
                saving = true
                runCatching { OmsApiClient.activate(token, password) }
                    .onSuccess { onActivated() }
                    .onFailure { error = it.message ?: LocalizationManager.t("activation_error") }
                saving = false
            }
        }, enabled = !saving && password.isNotBlank() && confirmation.isNotBlank()) { Text(LocalizationManager.t("activate_account")) }
        if (saving) ContentState(LocalizationManager.t("save_in_progress"), loading = true)
        }
    }
}
