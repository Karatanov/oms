package oms.urp3.service

private val sourceNumberWhitespace = Regex("\\s|\u00a0|\u202f")
private val sourceCommaGroups = Regex("[+-]?[1-9]\\d{0,2}(,\\d{3})+")
private val sourceDotGroups = Regex("[+-]?[1-9]\\d{0,2}(\\.\\d{3}){2,}")
private val sourceUsDecimal = Regex("[+-]?\\d{1,3}(,\\d{3})+\\.\\d+(?:[eE][+-]?\\d+)?")
private val sourceEuropeanDecimal = Regex("[+-]?\\d{1,3}(\\.\\d{3})+,\\d+(?:[eE][+-]?\\d+)?")

/** Normalize supported source conventions without guessing malformed mixed grouping.
 * A single dot remains decimal; comma-only percentages keep their existing decimal convention.
 * Unit suffixes and numeric range/sign validation remain the caller's responsibility.
 */
internal fun normalizeSourceNumericText(value: String, percentage: Boolean = false): String? {
    val compact = value.replace(sourceNumberWhitespace, "")
    return when {
        ',' in compact && '.' in compact -> when {
            sourceUsDecimal.matches(compact) -> compact.replace(",", "")
            sourceEuropeanDecimal.matches(compact) -> compact.replace(".", "").replace(',', '.')
            else -> null
        }
        !percentage && sourceCommaGroups.matches(compact) -> compact.replace(",", "")
        !percentage && sourceDotGroups.matches(compact) -> compact.replace(".", "")
        else -> compact.replace(',', '.')
    }
}
