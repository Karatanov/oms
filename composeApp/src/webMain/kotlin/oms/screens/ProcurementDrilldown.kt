package oms.screens

/** Local table filters; the tranche belongs to the chart/page and stays outside. */
internal data class ProcurementTableFilters(
    val search: String = "", val oblast: String? = null, val subproject: String? = null,
    val method: String? = null, val status: String? = null, val month: String? = null, val contractType: String? = null
) {
    fun forChart(status: String, month: String? = null, contractType: String? = null) =
        ProcurementTableFilters(status = status, month = month, contractType = contractType)
}

/** A chart counts procedures/contracts; its supporting list identifies unique SPs. */
internal fun procurementChartSubprojectCodes(codes: List<String>): List<String> = codes
    .map { oms.urp3.paymentSubprojectKey(it).uppercase() }.filter(String::isNotBlank).distinct().sorted()

internal fun procurementChartCodeLines(codes: List<String>): String = procurementChartSubprojectCodes(codes)
    .chunked(3).joinToString("\n") { it.joinToString(", ") }
