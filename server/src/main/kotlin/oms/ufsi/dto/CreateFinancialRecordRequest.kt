package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateFinancialRecordRequest(
    val recordType: String, val referenceNumber: String, val amount: Double,
    val currency: String = "EUR", val recordDate: String, val paymentDate: String? = null,
    val description: String? = null, val milestone: String? = null,
    val paymentPurpose: String = "works"
)
