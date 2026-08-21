package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class FinancialImportErrorResponse(val row: Int, val field: String? = null, val message: String)

@Serializable
data class FinancialImportResponse(val imported: Int, val skipped: Int = 0, val errors: List<FinancialImportErrorResponse>)

@Serializable
data class FinancialRecordListResponse(
    val data: List<FinancialRecordResponse>,
    val summary: FinancialSummaryResponse
)

/** A financial record together with the project it belongs to, for global lists. */
@Serializable
data class FinancialRecordListItemResponse(
    val projectUuid: String,
    val record: FinancialRecordResponse
)
