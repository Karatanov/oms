package oms.screens

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import oms.data.OmsApiClient
import oms.localization.LocalizationManager
import oms.components.passwordRequirement
import oms.components.PasswordGuidance
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
    val confirmationFocus = remember { FocusRequester() }
    var confirmationVisited by remember { mutableStateOf(false) }
    var confirmationFocused by remember { mutableStateOf(false) }
    var confirmationSubmitted by remember { mutableStateOf(false) }
    val confirmationFinished = confirmationSubmitted || (confirmationVisited && !confirmationFocused)
    val confirmationError = when {
        confirmation.isEmpty() && password.isNotEmpty() && confirmationFinished -> "confirm_password_required"
        confirmation.isNotEmpty() && confirmation != password &&
            (confirmationFinished || !password.startsWith(confirmation)) -> "password_mismatch"
        else -> null
    }
    val missingRequirement = passwordRequirement(password)
    val canSubmit = !saving && missingRequirement == null && confirmation == password
    val activate: () -> Unit = {
        if (canSubmit) {
            saving = true
            error = null
            scope.launch {
                runCatching { OmsApiClient.activate(token, password) }
                    .onSuccess { onActivated() }
                    .onFailure { error = it.message ?: LocalizationManager.t("activation_error") }
                saving = false
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LanguageSwitcher()
        Text(LocalizationManager.t("activate_title"), style = MaterialTheme.typography.headlineMedium)
        oms.components.OmsPasswordField(
            password, { password = it; error = null }, LocalizationManager.t("password"),
            enabled = !saving,
            supportingText = { PasswordGuidance(password, missingRequirement) },
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { confirmationFocus.requestFocus() })
        )
        oms.components.OmsPasswordField(
            confirmation, { confirmation = it; confirmationSubmitted = false; error = null }, LocalizationManager.t("confirm_password"),
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().focusRequester(confirmationFocus).onFocusChanged {
                confirmationFocused = it.isFocused
                if (it.isFocused) confirmationVisited = true
            },
            isError = confirmationError != null,
            supportingText = when {
                confirmationError != null -> ({ Text(LocalizationManager.t(confirmationError)) })
                confirmation.isNotEmpty() && confirmation == password -> ({ Text(LocalizationManager.t("passwords_match")) })
                else -> null
            },
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { confirmationSubmitted = true; activate() })
        )
        error?.let { ContentState(it, error = true) }
        Button(onClick = activate, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) {
            Text(LocalizationManager.t(if (saving) "save_in_progress" else "activate_account"))
        }
        if (saving) ContentState(LocalizationManager.t("save_in_progress"), loading = true)
        }
    }
}
