package oms.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import oms.localization.Language
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("setOmsLanguage")
external fun setOmsLanguage(language: String)

@Composable
fun LanguageSwitcher() {
    Button(
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        onClick = {
            LocalizationManager.switchLanguage()
            setOmsLanguage(if (LocalizationManager.currentLanguage == Language.UK) "uk" else "en")
        }
    ) {
        Text(
            if (LocalizationManager.currentLanguage == Language.UK)
                "EN"
            else
                "UA"
        )
    }
}
