package oms.urp3

/** Canonical keys are shared by aggregation and labels, never translated before grouping. */
fun financialSectorKey(value: String): String {
    val parts = value.replace('\u00a0', ' ').trim().lowercase().split('/')
        .map { it.trim().replace(Regex("\\s+"), " ") }
    fun has(vararg aliases: String) = parts.any { part -> aliases.any { part == it } }
    fun contains(vararg stems: String) = parts.any { part -> stems.any { part.contains(it) } }
    return when {
        has("", "unspecified", "не визначено", "не вказано") && parts.all { it.isBlank() || it == "unspecified" || it == "не визначено" || it == "не вказано" } -> "Unspecified"
        contains("укрит", "shelter", "civil protection") -> "shelter"
        has("security_centres", "security centres", "security centers", "safety centres", "safety centers", "центри безпеки", "центр безпеки") -> "security_centres"
        has("education", "освіта") -> "education"
        has("health", "healthcare", "health care", "охорона здоров'я", "охорона здоров’я") -> "healthcare"
        has("housing", "житло") -> "housing"
        has("water_supply_sewerage", "water supply & sewerage", "water supply and sewerage", "water and sewerage", "водопостачання та водовідведення", "водопостачання і водовідведення") -> "water_supply_sewerage"
        has("water_supply", "water supply", "водопостачання") -> "water_supply"
        has("sewerage", "sewage", "wastewater", "водовідведення", "каналізація") -> "sewerage"
        has("heat_supply", "heat supply", "heating", "теплопостачання") -> "heat_supply"
        else -> value.substringBefore('/').trim()
    }
}

/** A broad Allocation caption must not erase a known detailed monitoring sector. */
fun preferredSourceSector(incoming: String, previous: String): String = when {
    incoming.isBlank() -> previous
    financialSectorKey(incoming) == "water_supply_sewerage" && financialSectorKey(previous) in setOf("water_supply", "sewerage") -> previous
    else -> incoming
}

fun financialConstructionKey(value: String): String {
    val parts = value.replace('\u00a0', ' ').trim().lowercase().split('/')
        .map { it.trim().replace(Regex("\\s+"), " ") }
    fun has(vararg aliases: String) = parts.any { part -> aliases.any { part == it } }
    return when {
        has("new_construction", "new construction", "нове будівництво") -> "new_construction"
        // Customer-approved OMS grouping: renovation/restoration are reconstruction.
        // Overhaul/capital repair remains a distinct construction activity.
        has("reconstruction", "реконструкція", "renovation", "restoration", "реставрація") -> "reconstruction"
        has("capital_repair", "capital repair", "капітальний ремонт", "overhaul", "major repair", "major repairs") -> "capital_repair"
        has("repair", "поточний ремонт") -> "repair"
        has("unspecified", "не визначено", "не вказано", "") -> "Unspecified"
        else -> value.substringBefore('/').trim()
    }
}
