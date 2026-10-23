package oms.urp3.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import oms.urp3.repository.PaymentMonitoringRepository
import oms.urp3.repository.PaymentMonitoringSnapshot
import org.slf4j.LoggerFactory

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
    private val logger = LoggerFactory.getLogger(javaClass)
    data class Snapshot(
        val refreshedAt: String,
        val items: List<PaymentMonitoringWorkbookSource.Item>
    )

    private data class CachedSnapshot(val snapshot: Snapshot, val expiresAt: Instant)

    private var cached: CachedSnapshot? = null
    @Volatile private var automaticRefreshStarted = false
    private var automaticRefreshExecutor: ScheduledExecutorService? = null

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

    /**
     * Optional server-side refresh for the approved protected workbook. The
     * scheduler is enabled only when its explicit environment flag is set;
     * this prevents unauthenticated polling of a private Google spreadsheet.
     */
    @Synchronized
    fun startAutomaticSourceRefresh(initialDelayMinutes: Long, intervalHours: Long) {
        if (automaticRefreshStarted) return
        automaticRefreshStarted = true
        automaticRefreshExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "payment-monitoring-auto-refresh").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay({
                try {
                    importWorkbook(PaymentMonitoringWorkbookSource.downloadWorkbook())
                    logger.info("Scheduled payment-monitoring refresh completed.")
                } catch (error: Exception) {
                    logger.warn("Scheduled payment-monitoring refresh failed; retained the previous snapshot.", error)
                }
            }, initialDelayMinutes.coerceAtLeast(1), intervalHours.coerceAtLeast(1) * 60, TimeUnit.MINUTES)
        }
        logger.info(
            "Scheduled payment-monitoring refresh enabled: every {} hour(s), first run in {} minute(s).",
            intervalHours.coerceAtLeast(1), initialDelayMinutes.coerceAtLeast(1)
        )
    }

    @Synchronized
    fun stopAutomaticSourceRefresh() {
        automaticRefreshExecutor?.shutdownNow()
        automaticRefreshExecutor = null
        automaticRefreshStarted = false
    }

    private fun PaymentMonitoringSnapshot.toSnapshot() = Snapshot(
        refreshedAt = updatedAt.toString(),
        items = items
    )

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(10)
    }
}
