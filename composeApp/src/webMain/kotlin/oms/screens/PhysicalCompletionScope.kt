package oms.screens

import oms.data.ApiSubprojectProgress

internal data class PhysicalCompletionSeries(
    val items: List<ApiSubprojectProgress>, val unavailableProjects: Int, val excludedCodes: List<String>
)

/** A known zero is renderable; a wire compatibility placeholder marked incomplete is not. */
internal fun physicalCompletionSeries(selected: List<ApiSubprojectProgress>): PhysicalCompletionSeries {
    val (valid, invalid) = selected.partition { it.hasCompleteAmounts && it.completionPct.isFinite() }
    return PhysicalCompletionSeries(valid, invalid.size, invalid.map { it.code.ifBlank { it.projectUuid } })
}
