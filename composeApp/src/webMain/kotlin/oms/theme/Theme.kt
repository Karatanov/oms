package oms.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 🔹 Світла тема (основна)
private val LightColors = lightColorScheme(

    primary = Primary,
    onPrimary = OnPrimary,

    primaryContainer = PrimaryLight,
    onPrimaryContainer = OnSurface,

    background = Background,
    onBackground = OnSurface,

    surface = Surface,
    onSurface = OnSurface,
)

// 🔹 Основна тема додатку
@Composable
fun OMSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}