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
            Language.UK -> match?.ukrainian ?: procurementValue(normalized)
            Language.EN -> match?.english ?: procurementValue(normalized)
        }
    }

    /**
     * Procurement imports retain the Ukrainian and English source wording as
     * `Український текст / English text`. Keep that stable source value for
     * filtering, while presenting only the currently selected language.
     */
    fun procurementValue(value: String): String {
        val normalized = value.trim()
        val separatorIndex = normalized.indexOf(" / ")
        if (separatorIndex < 0) return normalized
        return when (currentLanguage) {
            Language.UK -> normalized.substring(0, separatorIndex).trim()
            Language.EN -> normalized.substring(separatorIndex + 3).trim().ifBlank { normalized }
        }
    }

    /** Localizes the fixed HSE checklist used by standard SIR workbooks. */
    fun hseObservation(value: String): String {
        if (currentLanguage == Language.EN) return value
        val key = value.trim().trimEnd('.').lowercase()
        return when (key) {
            "all workers wear ppe equipment as relevant" -> "Усі працівники використовують відповідні засоби індивідуального захисту."
            "the fire shield / firefighting equipment is present at site" -> "На майданчику наявні пожежний щит / засоби пожежогасіння."
            "the site is appropriately fenced" -> "Майданчик належним чином огороджений."
            "there is lavatories on the site" -> "На майданчику є санітарно-побутові приміщення."
            "there are safety briefing logs" -> "Наявні журнали інструктажів з охорони праці."
            "safety information plate is in tact" -> "Інформаційний стенд з охорони праці у належному стані."
            "comment" -> t("comment")
            else -> value
        }
    }
}
