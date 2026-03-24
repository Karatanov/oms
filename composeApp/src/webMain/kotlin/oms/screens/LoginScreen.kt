package oms.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import oms.components.FeatureItem

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {

    // ---------------- STATE ----------------

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
                    text = "Monitor projects\nwith clarity and control",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )

                // 🔹 Підзаголовок
                Text(
                    text = "A unified system for project tracking, inspections, and financial oversight.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                )

                Spacer(Modifier.height(16.dp))

                // 🔹 Невеликий список переваг (дуже сильно піднімає UX)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    FeatureItem("Real-time project monitoring")
                    FeatureItem("Integrated inspection reports")
                    FeatureItem("Financial transparency tools")
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
                            text = "Welcome back",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Text(
                            text = "Please sign in to continue",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // ---------------- EMAIL ----------------
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email") },
                        placeholder = { Text("you@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ---------------- PASSWORD ----------------
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
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
                                    contentDescription = "Toggle password"
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
                                onCheckedChange = { rememberMe = it }
                            )
                            Text("Remember me")
                        }

                        TextButton(onClick = { }) {
                            Text("Forgot password?")
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
                        onClick = {

                            if (email.isBlank()) {
                                errorMessage = "Email is required"
                                return@Button
                            }

                            if (password.isBlank()) {
                                errorMessage = "Password is required"
                                return@Button
                            }

                            isLoading = true

                            // 🔹 mock auth
                            if (email == "admin@test.com" && password == "1234") {
                                onLoginSuccess()
                            } else {
                                errorMessage = "Invalid credentials"
                            }

                            isLoading = false
                        },
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
                            Text("Sign in")
                        }
                    }
                }
            }
        }
    }
}
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Visibility
//import androidx.compose.material.icons.filled.VisibilityOff
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.text.input.PasswordVisualTransformation
//import androidx.compose.ui.text.input.VisualTransformation
//import androidx.compose.ui.unit.dp
//
//@Composable
//fun LoginScreen(
//    onLoginSuccess: () -> Unit // навігація після успішного логіну
//) {
//
//    // ---------------- STATE ----------------
//
//    // 🔹 Стан полів вводу
//    var email by remember { mutableStateOf("") }
//    var password by remember { mutableStateOf("") }
//
//    // 🔹 UI стани
//    var rememberMe by remember { mutableStateOf(false) }
//    var passwordVisible by remember { mutableStateOf(false) }
//
//    // 🔹 Стан помилки
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//
//    // 🔹 Стан блокування акаунта
//    var isLocked by remember { mutableStateOf(false) }
//    var lockedUntil by remember { mutableStateOf<String?>(null) }
//
//    // 🔹 Стан завантаження (імітація API)
//    var isLoading by remember { mutableStateOf(false) }
//
//    // 🔹 Лічильник невдалих спроб (імітація backend-логіки)
//    var failedAttempts by remember { mutableStateOf(0) }
//
//    // ---------------- UI ----------------
//
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//
//        Card(
//            modifier = Modifier.width(420.dp),
//            elevation = CardDefaults.cardElevation(8.dp)
//        ) {
//
//            Column(
//                modifier = Modifier.padding(24.dp),
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//
//                // 🔹 Заголовок системи
//                Text(
//                    text = "Online Monitoring System",
//                    style = MaterialTheme.typography.headlineSmall
//                )
//
//                // ---------------- EMAIL ----------------
//
//                OutlinedTextField(
//                    value = email,
//                    onValueChange = {
//                        email = it
//
//                        // 🔹 Очищення помилки при зміні вводу
//                        errorMessage = null
//                    },
//                    label = { Text("Email") },
//                    placeholder = { Text("your@email.com") },
//                    singleLine = true,
//                    isError = email.isNotEmpty() && !isValidEmail(email)
//                )
//
//                // ---------------- PASSWORD ----------------
//
//                OutlinedTextField(
//                    value = password,
//                    onValueChange = {
//                        password = it
//                        errorMessage = null
//                    },
//                    label = { Text("Password") },
//                    singleLine = true,
//
//                    // 🔹 Маскування пароля
//                    visualTransformation =
//                        if (passwordVisible) VisualTransformation.None
//                        else PasswordVisualTransformation(),
//
//                    // 🔹 Іконка "око" для показу/приховування
//                    trailingIcon = {
//                        IconButton(onClick = {
//                            passwordVisible = !passwordVisible
//                        }) {
//                            Icon(
//                                imageVector = if (passwordVisible)
//                                    Icons.Default.Visibility
//                                else
//                                    Icons.Default.VisibilityOff,
//                                contentDescription = "Toggle password visibility"
//                            )
//                        }
//                    }
//                )
//
//                // ---------------- REMEMBER ME ----------------
//
//                Row(
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Checkbox(
//                        checked = rememberMe,
//                        onCheckedChange = { rememberMe = it }
//                    )
//                    Text("Remember me")
//                }
//
//                // ---------------- ERROR / LOCK STATE ----------------
//
//                when {
//                    isLocked -> {
//                        Text(
//                            text = "Account locked until $lockedUntil",
//                            color = MaterialTheme.colorScheme.error
//                        )
//                    }
//
//                    errorMessage != null -> {
//                        Text(
//                            text = errorMessage!!,
//                            color = MaterialTheme.colorScheme.error
//                        )
//                    }
//                }
//
//                // ---------------- LOGIN BUTTON ----------------
//
//                Button(
//                    onClick = {
//
//                        // 🔹 Блокування — не даємо натиснути
//                        if (isLocked) return@Button
//
//                        // 🔹 Базова валідація перед API
//                        if (!isValidEmail(email)) {
//                            errorMessage = "Invalid email format"
//                            return@Button
//                        }
//
//                        if (password.isBlank()) {
//                            errorMessage = "Password cannot be empty"
//                            return@Button
//                        }
//
//                        // 🔹 Імітація API виклику
//                        isLoading = true
//                        errorMessage = null
//
//                        // ---------------- MOCK AUTH ----------------
//
//                        when {
//                            email != "admin@test.com" -> {
//                                errorMessage = "User not found"
//                                failedAttempts++
//                            }
//
//                            password != "1234" -> {
//                                errorMessage = "Incorrect password"
//                                failedAttempts++
//                            }
//
//                            else -> {
//                                // 🔹 Успішний логін
//                                failedAttempts = 0
//                                onLoginSuccess()
//                            }
//                        }
//
//                        // 🔹 Логіка блокування після 5 спроб
//                        if (failedAttempts >= 5) {
//                            isLocked = true
//                            lockedUntil = "15:30" // mock значення
//                        }
//
//                        isLoading = false
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = !isLoading && !isLocked
//                ) {
//
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(18.dp),
//                            strokeWidth = 2.dp
//                        )
//                    } else {
//                        Text("Login")
//                    }
//                }
//
//                // ---------------- FORGOT PASSWORD ----------------
//
//                TextButton(
//                    onClick = {
//                        // 🔹 Тут буде навігація на forgot-password screen
//                    }
//                ) {
//                    Text("Forgot password?")
//                }
//            }
//        }
//    }
//}
//
//// ---------------- HELPERS ----------------
//
//// 🔹 Простий email validator (можна замінити на більш строгий)
//fun isValidEmail(email: String): Boolean {
//    return Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$").matches(email)
//}