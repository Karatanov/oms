package oms.components

/** Formats stored integer cents without floating-point rounding or Int overflow. */
fun formatEuroCents(cents: Long): String {
    val digits = cents.toString().removePrefix("-").padStart(3, '0')
    val whole = digits.dropLast(2).reversed().chunked(3).joinToString(" ").reversed()
    return (if (cents < 0) "−" else "") + "€ " + whole + "." + digits.takeLast(2)
}
