package oms.theme

import androidx.compose.ui.graphics.Color

// 🔹 Основний бренд-колір
val Primary = Color(0xFF278DAD)

// 🔹 Варіації (трохи темніша і світліша)
// 🔹 Використовуються для hover / active / gradients
//val PrimaryDark = Color(0xFF1F6F8A)
val PrimaryLight = Color(0xFF5FAFC9)

// 🔹 Нейтральні кольори (фон)
val Background = Color(0xFFF4F7F9)
val Surface = Color(0xFFFFFFFF)

// 🔹 Текст
// 4.89:1 contrast on the unchanged primary; white small text reaches only 3.82:1.
val OnPrimary = Color(0xFF00151C)
val OnSurface = Color(0xFF20313C)

/** Semantic foregrounds for subtle informational badges. */
object OmsColors {
    val Success = Color(0xFF246644)
    val Warning = Color(0xFF85520B)
    val Danger = Color(0xFFAC3038)
    val Information = Color(0xFF226787)
    val Neutral = Color(0xFF536571)
    val Purple = Color(0xFF72528C)
}

// 🔹 Error залишаємо стандартний (Material)
