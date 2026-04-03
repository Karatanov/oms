package oms.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import oms.localization.Language
import oms.localization.LocalizationManager

@Composable
fun LanguageSwitcher() {
    Button(
        onClick = { LocalizationManager.switchLanguage() }
    ) {
        Text(
            if (LocalizationManager.currentLanguage == Language.UK)
                "UA"
            else
                "EN"
        )
    }
}