package oms.urp3

/** Remove the contract suffix before the lot suffix: KH08_07#Lot1_W -> KH08_07. */
fun paymentSubprojectKey(code: String): String {
    val reference = code.trim().substringBefore('$')
    val suffix = reference.substringAfterLast('_').uppercase()
    val projectOrLot = if (suffix in setOf("W", "TS", "CSC")) reference.substringBeforeLast('_') else reference
    return projectOrLot.substringBefore('#').trim()
}

/** Excel stores percentage-formatted numeric cells as fractions; APIs use percentage points. */
fun excelPercentagePoints(value: Double?, percentageFormatted: Boolean): Double? =
    value?.takeIf { it.isFinite() }?.let { if (percentageFormatted) it * 100.0 else it }
