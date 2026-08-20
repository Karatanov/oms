package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import oms.components.FeatureItem
import oms.data.BrowserCredentialStorage
import oms.data.OmsApiClient
import oms.data.ApiUser
import oms.localization.LocalizationManager

@Composable
fun LoginScreen(
    onLoginSuccess: (ApiUser) -> Unit
) {

    // ---------------- STATE ----------------

    val savedCredentials = remember { BrowserCredentialStorage.load() }
    var username by remember { mutableStateOf(savedCredentials?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(savedCredentials != null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submitLogin() {
        if (isLoading) return

        if (username.isBlank()) {
            errorMessage = LocalizationManager.t("login_or_email_required")
            return
        }

        if (password.isBlank()) {
            errorMessage = LocalizationManager.t("password_required")
            return
        }

        isLoading = true
        scope.launch {
            val loginResult = runCatching {
                OmsApiClient.login(username, password)
            }
            isLoading = false
            loginResult
                .onSuccess { authenticated ->
                    if (rememberMe) {
                        BrowserCredentialStorage.save(username.trim())
                    } else {
                        BrowserCredentialStorage.clear()
                    }
                    onLoginSuccess(authenticated)
                }
                .onFailure { exception ->
                    errorMessage = LocalizationManager.t("connection_error").replace("{message}", exception.message ?: LocalizationManager.t("unknown_error"))
                }
        }
    }

    // ---------------- LAYOUT ----------------

    Row(modifier = Modifier.fillMaxSize()) {

// ---------------- LEFT PANEL (enhanced branding) ----------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // 🔹 Градієнт замість плоского кольору
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                )
                .padding(64.dp),
            contentAlignment = Alignment.CenterStart
        ) {

            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = 480.dp)
            ) {

                // 🔹 Маленький badge (додає "продуктовість")
                Surface(
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "OMS Platform",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // 🔥 ГОЛОВНИЙ АКЦЕНТ (hero text)
                Text(
                    text = LocalizationManager.t("login_hero_title"),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // 🔹 Підзаголовок
                Text(
                    text = LocalizationManager.t("login_hero_subtitle"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )

                Spacer(Modifier.height(16.dp))

                // 🔹 Невеликий список переваг (дуже сильно піднімає UX)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    FeatureItem(LocalizationManager.t("feature_realtime_monitoring"))
                    FeatureItem(LocalizationManager.t("feature_inspection_reports"))
                    FeatureItem(LocalizationManager.t("feature_financial_tools"))
                }
            }
        }

        // ---------------- RIGHT PANEL (form) ----------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {

            Card(
                modifier = Modifier.width(460.dp),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {

                Column(
                    modifier = Modifier.padding(36.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {

                    // 🔹 Заголовок форми
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                        Text(
                            text = LocalizationManager.t("welcome_back"),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            text = LocalizationManager.t("please_sign_in"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // ---------------- LOGIN ----------------
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text(LocalizationManager.t("login_or_email")) },
                        placeholder = { Text(LocalizationManager.t("login_or_email_example")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---------------- PASSWORD ----------------
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text(LocalizationManager.t("password")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        modifier = Modifier.fillMaxWidth(),

                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),

                        trailingIcon = {
                            IconButton(onClick = {
                                passwordVisible = !passwordVisible
                            }) {
                                Icon(
                                    imageVector = if (passwordVisible)
                                        Icons.Default.Visibility
                                    else
                                        Icons.Default.VisibilityOff,
                                    contentDescription = LocalizationManager.t("toggle_password")
                                )
                            }
                        }
                    )

                    // ---------------- OPTIONS ----------------
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { selected ->
                                    rememberMe = selected
                                    if (!selected) BrowserCredentialStorage.clear()
                                }
                            )
                            Text(LocalizationManager.t("remember_me"))
                        }

                        TextButton(onClick = { }) {
                            Text(LocalizationManager.t("forgot_password"))
                        }
                    }

                    // ---------------- ERROR ----------------
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // ---------------- LOGIN BUTTON ----------------
                    Button(
                        onClick = ::submitLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(LocalizationManager.t("sign_in"))
                        }
                    }
                }
            }
        }
    }
}
