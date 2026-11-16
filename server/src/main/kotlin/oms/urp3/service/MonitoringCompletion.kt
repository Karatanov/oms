package oms.urp3.service

/** Physical work completion is a fraction of a whole, unlike financial overspend ratios. */
internal fun monitoringCompletionFraction(value: String?): Double? {
    val raw = value.orEmpty().trim().replace('\u00a0', ' ')
    if (raw.isBlank()) return null
    val number = raw.replace("%", "").replace(" ", "").replace(',', '.').toDoubleOrNull() ?: return null
    val fraction = if ('%' in raw || number > 1.0) number / 100.0 else number
    return fraction.takeIf { it.isFinite() && it in 0.0..1.0 }
}

internal fun validateMonitoringCompletion(value: String, code: String, cell: String) {
    require(value.isBlank() || monitoringCompletionFraction(value) != null) {
        "Invalid physical work completion for $code at $cell: '$value'. Expected 0–100%. Correct the source workbook; the existing registry was not replaced."
    }
}

/** A valid reported completion wins; an invalid source must never be hidden by an ACT fallback. */
internal fun physicalWorkCompletedAmount(
    reportedAmount: Double?, actualAmount: Double?, hasActualAmount: Boolean, invalidSource: Boolean
): Double? = when {
    invalidSource -> null
    reportedAmount != null -> reportedAmount.takeIf(Double::isFinite)
    hasActualAmount -> actualAmount?.takeIf(Double::isFinite)
    else -> 0.0
}
