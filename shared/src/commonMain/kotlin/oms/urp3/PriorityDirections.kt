package oms.urp3

/** Registry priority directions retain shelter subtypes, unlike sector-level charts. */
fun priorityDirectionKey(value: String): String {
    val parts = value.replace('\u00a0', ' ').trim().lowercase().split('/')
        .map { it.trim().replace(Regex("\\s+"), " ") }
    return when {
        parts.any { it == "shelter_education_health" || it.startsWith("shelters: education and health buildings") ||
            it.startsWith("укриття: заклади освіти та охорони здоров") } -> "shelter_education_health"
        parts.any { it == "shelter_other_public" || it.startsWith("shelters: other public buildings") ||
            it.startsWith("укриття: інші громадські будівлі") } -> "shelter_other_public"
        else -> financialSectorKey(value)
    }
}
