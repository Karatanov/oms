package oms.localization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LocalizationManager {

    var currentLanguage by mutableStateOf(Language.UK)

    fun t(key: String): String {
        return when (currentLanguage) {
            Language.UK -> Strings.uk[key]
            Language.EN -> Strings.en[key]
        } ?: key
    }

    fun switchLanguage() {
        currentLanguage =
            if (currentLanguage == Language.UK)
                Language.EN
            else
                Language.UK
    }
}