package oms.urp3.service

/** DB Table owns all supplied columns, including blanks; Allocation is only a supplement. */
internal fun mergeMonitoringSnapshotValues(
    previous: Map<String, String>, incoming: Map<String, String>, allocation: Boolean
): Map<String, String> = previous + if (allocation) incoming.filterValues(String::isNotBlank) else incoming
