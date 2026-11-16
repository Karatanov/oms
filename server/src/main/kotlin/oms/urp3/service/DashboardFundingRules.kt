package oms.urp3.service

import kotlinx.serialization.json.Json

/** The lot owns its contract; the parent code is only the aggregation key. */
internal fun worksContractSourceCode(subproject: String, lot: String?): String =
    lot?.trim()?.takeIf(String::isNotBlank)?.uppercase() ?: subproject.trim().uppercase()

/** Approved EIB funding is neither signed contract value nor total cost including local finance. */
internal fun monitoringApprovedEibUah(registryJson: String?): Double? = registryJson
    ?.let { runCatching { Json.decodeFromString<Map<String, String>>(it)["eibCost"] }.getOrNull() }
    ?.let(::monitoringSourceAmount)

/** Source snapshots can contain Excel display grouping, not just decimal JSON strings. */
internal fun monitoringSourceAmount(value: String): Double? {
    val normalized = normalizeSourceNumericText(value) ?: return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

/** A rejected mixed format must abort import, not erase a saved budget via the missing-value fallback. */
internal fun monitoringImportAmount(raw: String, sourceField: String): Double {
    require(!(',' in raw && '.' in raw) || normalizeSourceNumericText(raw) != null) {
        "Invalid numeric separators for $sourceField. Correct the source workbook."
    }
    return monitoringSourceAmount(raw) ?: 0.0
}
