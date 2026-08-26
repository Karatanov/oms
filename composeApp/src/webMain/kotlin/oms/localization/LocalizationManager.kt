package oms.localization

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LocalizationManager {

    private data class ProcurementStatusText(val ukrainian: String, val english: String)

    private val procurementStatuses = listOf(
        ProcurementStatusText("Не розпочато", "Not Started"),
        ProcurementStatusText("Закупівля триває", "Tender Ongoing"),
        ProcurementStatusText("Повідомлення про намір укласти договір", "Contract award notice"),
        ProcurementStatusText("Договір укладено", "Contract signed"),
        ProcurementStatusText("Відмінено", "Cancelled"),
        ProcurementStatusText("Договір розірвано", "Contract terminated")
    )

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

    /**
     * Procurement statuses are stored in their stable bilingual form for imports
     * and dashboard aggregation. The UI shows only the active-language variant.
     */
    fun procurementStatus(value: String): String {
        val normalized = value.trim()
        val match = procurementStatuses.firstOrNull { status ->
            normalized.equals(status.ukrainian, ignoreCase = true) ||
                normalized.equals(status.english, ignoreCase = true) ||
                normalized.equals("${status.ukrainian} / ${status.english}", ignoreCase = true)
        }
        return when (currentLanguage) {
            Language.UK -> match?.ukrainian ?: normalized.substringBefore(" / ").trim()
            Language.EN -> match?.english ?: normalized.substringAfter(" / ", normalized).trim()
        }
    }
}
