package oms.ufsi.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProjectAmountDto(
    val amount: String,
    val currency: String,
    val convertedAmount: String,
    val uahPerEur: String,
    val rateDate: String,
    val conversionEdited: Boolean = false
)

@Serializable
data class ProjectExchangeRateResponse(val uahPerEur: String, val date: String)
