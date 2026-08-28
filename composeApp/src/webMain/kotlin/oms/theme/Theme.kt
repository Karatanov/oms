package oms.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact enterprise scale; the corporate primary remains #278DAD. */
object OmsDimensions {
    val SpaceSmall = 8.dp
    val SpaceMedium = 16.dp
    val PagePadding = 24.dp
    val ControlHeight = 44.dp
    val IconSize = 20.dp
    val TableHeaderHeight = 52.dp
    val CornerRadius = 10.dp
}

private val LightColors = lightColorScheme(
    primary = Primary, onPrimary = OnPrimary,
    primaryContainer = Color(0xFFE0F0F5), onPrimaryContainer = Color(0xFF16566C),
    secondary = Color(0xFF496574), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EFF3), onSecondaryContainer = OnSurface,
    tertiary = OmsColors.Success, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5F2EB), onTertiaryContainer = OmsColors.Success,
    background = Background, onBackground = OnSurface,
    surface = Surface, onSurface = OnSurface,
    surfaceVariant = Color(0xFFECF1F4), onSurfaceVariant = OmsColors.Neutral,
    surfaceDim = Color(0xFFE6EDF1), surfaceBright = Surface,
    surfaceContainerLowest = Surface, surfaceContainerLow = Color(0xFFF8FAFB),
    surfaceContainer = Surface, surfaceContainerHigh = Color(0xFFF0F5F7),
    surfaceContainerHighest = Color(0xFFE7EFF3),
    outline = Color(0xFF8496A1), outlineVariant = Color(0xFFDCE5EA),
    error = OmsColors.Danger, onError = Color.White,
    errorContainer = Color(0xFFFCEBEC), onErrorContainer = Color(0xFF81252C),
    scrim = Color(0xFF142C39), inverseSurface = OnSurface,
    inverseOnSurface = Surface, inversePrimary = PrimaryLight
)

private val OmsTypography = Typography(
    displayMedium = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

// 🔹 Основна тема додатку
@Composable
fun OMSTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = OmsTypography,
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(18.dp)),
        content = content
    )
}
