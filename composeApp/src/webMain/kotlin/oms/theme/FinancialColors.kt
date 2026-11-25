package oms.theme

/** Same semantic series colors as procurement/payment chips, independent of sort order. */
fun paymentPurposeColor(purpose: String) = when (purpose) {
    "works", "W" -> OmsColors.Information
    "technical_supervision", "TS" -> OmsColors.Purple
    "engineer_consultant", "CSC" -> OmsColors.Success
    "equipment", "E" -> OmsColors.Warning
    else -> OmsColors.Neutral
}

object InvestmentColors {
    val Total = OmsColors.Information
    val Eib = OmsColors.Success
    val Local = OmsColors.Purple
}

fun procurementStatusColor(status: String) = when {
    status.contains("договір укладено", true) || status.contains("contract signed", true) -> OmsColors.Success
    status.contains("закупівля триває", true) || status.contains("tender ongoing", true) -> OmsColors.Information
    status.contains("повідомлення про намір", true) || status.contains("contract award notice", true) -> OmsColors.Warning
    status.contains("відмінено", true) || status.contains("розірвано", true) || status.contains("cancelled", true) || status.contains("terminated", true) -> OmsColors.Danger
    else -> OmsColors.Neutral
}
