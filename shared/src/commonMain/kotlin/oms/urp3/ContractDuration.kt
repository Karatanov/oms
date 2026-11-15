package oms.urp3

/** Source months are authoritative. Otherwise round days using the existing UI's
 * 365.25-day year convention (365.25 / 12 = 487 / 16 days), identically on JVM/JS/WASM.
 * Integer arithmetic avoids platform rounding differences and Long overflow.
 */
fun contractDurationMonths(days: Long?, sourceMonths: String? = null): String? {
    val source = sourceMonths?.trim()?.replace(',', '.')
    source?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let { return source }
    if (days == null || days < 0) return null
    val months = (days / 487) * 16 + ((days % 487) * 16 + 243) / 487
    return months.toString()
}
