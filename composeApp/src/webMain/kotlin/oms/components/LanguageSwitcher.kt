package oms.components

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

fun switchWorkspaceLanguage() {
    LocalizationManager.switchLanguage()
    setOmsLanguage(if (LocalizationManager.currentLanguage == Language.UK) "uk" else "en")
}

@Composable
fun LanguageSwitcher() {
    val description = LocalizationManager.t(
        if (LocalizationManager.currentLanguage == Language.UK) "switch_to_english" else "switch_to_ukrainian"
    )
    OmsTooltipBox(tooltip = { Text(description) }) {
    WorkspaceOutlinedButton(
        modifier = Modifier.chartControlFocus().semantics { contentDescription = description },
        onClick = ::switchWorkspaceLanguage
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
