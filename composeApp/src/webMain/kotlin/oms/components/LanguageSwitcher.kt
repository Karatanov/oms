package oms.components

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import oms.localization.Language
import oms.localization.LocalizationManager
import kotlin.js.JsName

@JsName("setOmsLanguage")
external fun setOmsLanguage(language: String)

@Composable
fun LanguageSwitcher() {
    val description = LocalizationManager.t(
        if (LocalizationManager.currentLanguage == Language.UK) "switch_to_english" else "switch_to_ukrainian"
    )
    OmsTooltipBox(tooltip = { Text(description) }) {
    Button(
        modifier = Modifier.buttonHandCursor().semantics { contentDescription = description },
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
}
