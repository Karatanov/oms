package oms.urp3.service

/** Null means no selection supplied; an empty selection must never expand to all rows. */
internal fun <T, K> selectTableExportRows(rows: List<T>, requestedKeys: List<K>?, key: (T) -> K): List<T> {
    if (requestedKeys == null) return rows
    if (requestedKeys.isEmpty()) return emptyList()
    val byKey = rows.associateBy(key)
    return requestedKeys.distinct().mapNotNull(byKey::get)
}

/** Reject malformed IDs rather than dropping them and silently exporting the full registry. */
internal fun procurementExportIds(rawIds: List<String>?, empty: String?): List<Long>? {
    require(empty == null || empty == "true" || empty == "false") { "Empty selection must be true or false." }
    if (rawIds != null) return rawIds.map { raw ->
        raw.toLongOrNull()?.takeIf { it > 0L }
            ?: throw IllegalArgumentException("Procurement export IDs must be positive integers.")
    }.distinct()
    return if (empty == "true") emptyList() else null
}
