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
    val compact = value.replace(Regex("\\s|\u00a0"), "")
    val normalized = when {
        compact.contains(',') && compact.contains('.') -> compact.replace(",", "")
        compact.matches(Regex("-?\\d{1,3}(,\\d{3})+")) -> compact.replace(",", "")
        else -> compact.replace(',', '.')
    }
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}
