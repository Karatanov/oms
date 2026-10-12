package oms.umitaf.dto

import kotlinx.serialization.Serializable

@Serializable
data class FinancialRecordResponse(
    val uuid: String, val recordType: String, val referenceNumber: String,
    val amount: Double, val currency: String, val recordDate: String,
    val paymentDate: String?, val description: String?, val milestone: String?,
    val paymentPurpose: String = "works",
    val eurExchangeRate: Double? = null,
    val eurExchangeDate: String? = null,
    val amountEurCents: Long? = null
)

@Serializable
data class FinancialProgressItemResponse(
    val label: String,
    val contractAmountUah: Double,
    val completedAmountUah: Double,
    val completionPct: Double
)

@Serializable
data class FinancialProgressResponse(
    val byRegion: List<FinancialProgressItemResponse> = emptyList(),
    val byConstructionType: List<FinancialProgressItemResponse> = emptyList()
)
