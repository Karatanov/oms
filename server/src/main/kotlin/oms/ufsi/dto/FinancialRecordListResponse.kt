package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class FinancialRecordListResponse(
    val data: List<FinancialRecordResponse>,
    val summary: FinancialSummaryResponse
)
