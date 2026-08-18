package oms.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import oms.localization.Language
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("setOmsLanguage")
external fun setOmsLanguage(language: String)

@Composable
fun LanguageSwitcher() {
    Button(
        onClick = {
            LocalizationManager.switchLanguage()
            setOmsLanguage(if (LocalizationManager.currentLanguage == Language.UK) "uk" else "en")
        }
    ) {
        Text(
            if (LocalizationManager.currentLanguage == Language.UK)
                "UA"
            else
                "EN"
        )
    }
}
