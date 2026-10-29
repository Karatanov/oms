package oms.components

/**
 * Formats an integer with non-breaking thousands separators.
 *
 * Chart labels and compact dashboard cards must not wrap a number between its
 * thousands groups; using a non-breaking space preserves both readability and
 * the intended single-line layout in the browser.
 */
fun formatGroupedNumber(value: Long): String {
    val digits = value.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString("\u00A0").reversed()
    return if (value < 0) "−$grouped" else grouped
}

/** Formats stored integer cents without floating-point rounding or Int overflow. */
fun formatEuroCents(cents: Long): String {
    val digits = cents.toString().removePrefix("-").padStart(3, '0')
    val whole = formatGroupedNumber(digits.dropLast(2).toLong())
    return (if (cents < 0) "−" else "") + "€ " + whole + "." + digits.takeLast(2)
}
