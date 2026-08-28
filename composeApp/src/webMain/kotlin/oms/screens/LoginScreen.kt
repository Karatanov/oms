package oms.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import oms.components.FeatureItem
import oms.components.WasmSafeOverlay
import oms.data.BrowserCredentialStorage
import oms.data.OmsApiClient
import oms.data.ApiUser
import oms.localization.LocalizationManager

@Composable
fun LoginScreen(
    onLoginSuccess: (ApiUser) -> Unit,
    onGuestAccess: () -> Unit
) {

    // ---------------- STATE ----------------

    val savedCredentials = remember { BrowserCredentialStorage.load() }
    var username by remember { mutableStateOf(savedCredentials?.username.orEmpty()) }
    var password by remember { mutableStateOf(savedCredentials?.password.orEmpty()) }

    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(savedCredentials != null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showPasswordReset by remember { mutableStateOf(false) }
    var isStartingGuestSession by remember { mutableStateOf(false) }
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
                        BrowserCredentialStorage.save(username.trim(), password)
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

    // Login uses light labels on filled buttons without changing the workspace theme.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onPrimary = Color.White)) {
    Box(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val showBrandPanel = maxWidth >= 1000.dp
    Row(modifier = Modifier.fillMaxSize()) {

// A quiet brand panel only when there is enough space for both columns.
        if (showBrandPanel) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // 🔹 Градієнт замість плоского кольору
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            androidx.compose.ui.graphics.Color(0xFF16566C)
                        )
                    )
                )
                .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 88.dp),
            contentAlignment = Alignment.CenterStart
        ) {

            Column(
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState())
            ) {

                // 🔹 Маленький badge (додає "продуктовість")
                Surface(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "OMS Platform",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // 🔥 ГОЛОВНИЙ АКЦЕНТ (hero text)
                Text(
                    text = LocalizationManager.t("login_hero_title"),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 52.sp),
                    color = androidx.compose.ui.graphics.Color.White
                )

                // 🔹 Підзаголовок
                Text(
                    text = LocalizationManager.t("login_hero_subtitle"),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 30.sp),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
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

        }
        // ---------------- RIGHT PANEL (form) ----------------
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 88.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {

            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {

                Column(
                    modifier = Modifier.padding(36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // Form identity
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

                        TextButton(onClick = { showPasswordReset = true }) {
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
                        enabled = !isLoading && !isStartingGuestSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Text(LocalizationManager.t("sign_in"))
                        }
                    }
                    Button(
                        onClick = {
                            isStartingGuestSession = true
                            scope.launch {
                                runCatching { OmsApiClient.startGuestSession() }
                                    .onSuccess { onGuestAccess() }
                                    .onFailure { errorMessage = LocalizationManager.t("guest_access_error") }
                                isStartingGuestSession = false
                            }
                        },
                        enabled = !isLoading && !isStartingGuestSession,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(LocalizationManager.t("continue_as_guest")) }
                }
            }
        }
    }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            oms.components.LanguageSwitcher()
        }
        if (showPasswordReset) {
            WasmSafeOverlay(onDismiss = { showPasswordReset = false }) {
                PasswordResetDialog(
                    initialIdentifier = username,
                    onDismiss = { showPasswordReset = false }
                )
            }
        }
    }
    }
}

@Composable
private fun PasswordResetDialog(initialIdentifier: String, onDismiss: () -> Unit) {
    var identifier by remember { mutableStateOf(initialIdentifier) }
    var isSending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Card(
        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(LocalizationManager.t("password_reset_title"), style = MaterialTheme.typography.titleLarge)
            Text(LocalizationManager.t("password_reset_hint"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = identifier,
                onValueChange = { identifier = it; error = null },
                label = { Text(LocalizationManager.t("login_or_email")) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (sent) Text(LocalizationManager.t("password_reset_sent"), color = MaterialTheme.colorScheme.primary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(LocalizationManager.t("close")) }
                Button(
                    onClick = {
                        isSending = true
                        scope.launch {
                            runCatching { OmsApiClient.requestPasswordReset(identifier.trim()) }
                                .onSuccess { sent = true }
                                .onFailure { error = LocalizationManager.t("password_reset_failed") }
                            isSending = false
                        }
                    },
                    enabled = identifier.isNotBlank() && !isSending
                ) {
                    if (isSending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(LocalizationManager.t("password_reset_send"))
                }
            }
        }
    }
}
