package oms.urp3.domain

/**
 * Contract classes defined by the approved procurement register.  The source
 * row identifier (for example `VN08_01_TS$1`) is authoritative because the
 * free-form Type cell can contain an erroneous cached spreadsheet value.
 */
enum class ProcurementContractType(val sourceCode: String, val displayValue: String) {
    WORKS("W", "Роботи / Works"),
    TECHNICAL_SUPERVISION("TS", "Технічний нагляд / TS"),
    ENGINEER_CONSULTANT("CSC", "Інженер-консультант / CSC");

    companion object {
        /** Resolves the explicit suffix first and only then falls back to a display value. */
        fun resolve(sourceId: String?, sourceValue: String?): ProcurementContractType? =
            fromSourceId(sourceId) ?: fromDisplayValue(sourceValue)

        fun fromSourceId(sourceId: String?): ProcurementContractType? = sourceId
            ?.substringBefore('$')
            ?.substringAfterLast('_')
            ?.trim()
            ?.uppercase()
            ?.let(::fromCode)

        fun fromCode(value: String?): ProcurementContractType? = entries.firstOrNull {
            it.sourceCode.equals(value?.trim(), ignoreCase = true)
        }

        fun fromDisplayValue(value: String?): ProcurementContractType? {
            val normalized = value.orEmpty().trim().lowercase()
            return when {
                normalized == "w" || normalized.contains("works") || normalized.contains("робот") -> WORKS
                normalized == "ts" || normalized.contains("technical supervision") || normalized.contains("техніч") -> TECHNICAL_SUPERVISION
                normalized == "csc" || normalized.contains("engineer consultant") || normalized.contains("engineer-consultant") ||
                    normalized.contains("інженер") || normalized.contains("консультант") -> ENGINEER_CONSULTANT
                else -> null
            }
        }
    }
}
