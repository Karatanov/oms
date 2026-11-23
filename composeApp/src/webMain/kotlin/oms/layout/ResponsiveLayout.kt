package oms.layout

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared by pages so phone layouts do not reserve desktop scroll-button lanes. */
val LocalMobileLayout = staticCompositionLocalOf { false }

@Composable
fun workspacePadding(withScrollControls: Boolean = false, desktop: Dp = oms.theme.OmsDimensions.PagePadding): PaddingValues =
    if (LocalMobileLayout.current) PaddingValues(
        start = 12.dp, top = 12.dp, end = 12.dp,
        bottom = if (withScrollControls) 80.dp else 12.dp
    ) else PaddingValues(
        start = desktop, top = desktop,
        end = if (withScrollControls) 76.dp else desktop, bottom = desktop
    )
