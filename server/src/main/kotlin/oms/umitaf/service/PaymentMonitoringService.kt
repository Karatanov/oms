package oms.umitaf.service

import java.time.Duration
import java.time.Instant

/**
 * Keeps the external monitoring workbook outside normal request latency while
 * preserving its read-only nature: no source row is written as an OMS act or
 * payment.
 */
class PaymentMonitoringService(
    private val now: () -> Instant = Instant::now,
    private val loader: () -> List<PaymentMonitoringWorkbookSource.Item> = PaymentMonitoringWorkbookSource::bundledSnapshot
) {
    data class Snapshot(
        val refreshedAt: String,
        val items: List<PaymentMonitoringWorkbookSource.Item>
    )

    private data class CachedSnapshot(val snapshot: Snapshot, val expiresAt: Instant)

    private var cached: CachedSnapshot? = null

    @Synchronized
    fun current(): Snapshot {
        val requestedAt = now()
        cached?.takeIf { requestedAt.isBefore(it.expiresAt) }?.let { return it.snapshot }
        val snapshot = Snapshot(refreshedAt = requestedAt.toString(), items = loader())
        cached = CachedSnapshot(snapshot, requestedAt.plus(CACHE_TTL))
        return snapshot
    }

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(10)
    }
}
