package oms.screens

import oms.data.ApiSubprojectProgress

internal data class PhysicalCompletionSeries(val items: List<ApiSubprojectProgress>, val unavailableProjects: Int)

/** A known zero is renderable; a wire compatibility placeholder marked incomplete is not. */
internal fun physicalCompletionSeries(selected: List<ApiSubprojectProgress>): PhysicalCompletionSeries {
    val unavailable = selected.count { !it.hasCompleteAmounts || !it.completionPct.isFinite() }
    return PhysicalCompletionSeries(if (unavailable == 0) selected else emptyList(), unavailable)
}
