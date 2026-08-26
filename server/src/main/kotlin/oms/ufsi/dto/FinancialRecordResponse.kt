package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class FinancialRecordResponse(
    val uuid: String, val recordType: String, val referenceNumber: String,
    val amount: Long, val currency: String, val recordDate: String,
    val paymentDate: String?, val description: String?, val milestone: String?,
    val paymentPurpose: String = "works"
)
