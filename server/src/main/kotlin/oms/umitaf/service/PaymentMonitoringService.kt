package oms.umitaf.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import oms.umitaf.repository.PaymentMonitoringRepository
import oms.umitaf.repository.PaymentMonitoringSnapshot

/**
 * Keeps the external monitoring workbook outside normal request latency while
 * preserving its read-only nature: no source row is written as an OMS act or
 * payment.
 */
class PaymentMonitoringService(
    private val repository: PaymentMonitoringRepository,
    private val now: () -> Instant = Instant::now,
    private val bundledLoader: () -> List<PaymentMonitoringWorkbookSource.Item> = PaymentMonitoringWorkbookSource::bundledSnapshot
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
        val persisted = repository.findSnapshot()
            ?: repository.replaceAll(bundledLoader(), LocalDateTime.now())
        val snapshot = persisted.toSnapshot()
        cached = CachedSnapshot(snapshot, requestedAt.plus(CACHE_TTL))
        return snapshot
    }

    @Synchronized
    fun importWorkbook(bytes: ByteArray): Snapshot {
        val imported = PaymentMonitoringWorkbookSource.parseWorkbook(bytes)
        val snapshot = repository.replaceAll(imported, LocalDateTime.now()).toSnapshot()
        cached = CachedSnapshot(snapshot, now().plus(CACHE_TTL))
        return snapshot
    }

    private fun PaymentMonitoringSnapshot.toSnapshot() = Snapshot(
        refreshedAt = updatedAt.toString(),
        items = items
    )

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(10)
    }
}
