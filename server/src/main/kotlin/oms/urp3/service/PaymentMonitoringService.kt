package oms.urp3.service

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
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

    /** Status of an administrator-triggered source refresh. */
    data class RefreshStatus(
        val id: String,
        val status: String,
        val progress: Int,
        val phase: String,
        val importedCount: Int? = null,
        val error: String? = null
    )

    private data class CachedSnapshot(val snapshot: Snapshot, val expiresAt: Instant)

    private var cached: CachedSnapshot? = null
    private val refreshLock = Any()
    @Volatile private var activeRefresh: RefreshJob? = null
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
        return replaceAll(PaymentMonitoringWorkbookSource.parseWorkbook(bytes))
    }

    /**
     * Starts a cancellable import from the configured approved Google source.
     * The download uses the server's service-account credentials when present;
     * an XLS/XLSX upload remains available as an administrator fallback.
     */
    fun startApprovedSourceRefresh(): RefreshStatus = synchronized(refreshLock) {
        startRefresh(null)
    }

    /** Starts the same validated replacement flow from an uploaded workbook. */
    fun startWorkbookRefresh(workbook: ByteArray): RefreshStatus = synchronized(refreshLock) {
        require(workbook.isNotEmpty()) { "Select an XLS or XLSX payment monitoring workbook." }
        require(workbook.size <= 20_000_000) { "The payment monitoring workbook must not exceed 20 MB." }
        startRefresh(workbook)
    }

    /** Starts the approved-source refresh with a short-lived administrator OAuth token. */
    fun startGoogleSourceRefresh(token: String): RefreshStatus = synchronized(refreshLock) {
        require(token.isNotBlank()) { "Google authorization is required." }
        startRefresh(null, token)
    }

    fun getRefresh(id: String): RefreshStatus? = activeRefresh
        ?.takeIf { it.id == id }
        ?.snapshot()

    /** A cancellation before replacement leaves the most recent valid snapshot untouched. */
    fun cancelRefresh(id: String): RefreshStatus? = synchronized(refreshLock) {
        val job = activeRefresh?.takeIf { it.id == id } ?: return null
        if (job.status == "RUNNING") {
            job.cancelRequested = true
            job.status = "CANCELLING"
            job.phase = "cancelling"
        }
        job.snapshot()
    }

    private fun startRefresh(uploadedWorkbook: ByteArray?, googleToken: String? = null): RefreshStatus {
        activeRefresh?.takeIf { it.status in setOf("RUNNING", "CANCELLING") }?.let {
            throw IllegalStateException("A payment-monitoring refresh is already running.")
        }
        val job = RefreshJob(UUID.randomUUID().toString(), uploadedWorkbook, googleToken).also {
            if (uploadedWorkbook != null) {
                it.progress = 15
                it.phase = "opening_workbook"
            }
        }
        activeRefresh = job
        Thread({ runRefresh(job) }, "payment-monitoring-refresh-${job.id.take(8)}").apply {
            isDaemon = true
            start()
        }
        return job.snapshot()
    }

    private fun runRefresh(job: RefreshJob) {
        try {
            val workbook = job.uploadedWorkbook ?: run {
                job.progress = 10
                job.phase = "downloading"
                job.googleToken?.let(PaymentMonitoringWorkbookSource::downloadWithUserToken)
                    ?: PaymentMonitoringWorkbookSource.downloadWorkbook()
            }
            checkCancelled(job)
            job.progress = 45
            job.phase = "opening_workbook"
            val items = PaymentMonitoringWorkbookSource.parseWorkbook(workbook)
            checkCancelled(job)
            job.progress = 80
            job.phase = "replacing"
            val snapshot = replaceAll(items)
            job.importedCount = snapshot.items.size
            job.progress = 100
            job.phase = "completed"
            job.status = "COMPLETED"
        } catch (_: RefreshCancelled) {
            job.progress = 0
            job.phase = "cancelled"
            job.status = "CANCELLED"
        } catch (error: Exception) {
            job.phase = "failed"
            job.status = "FAILED"
            job.error = error.message ?: "The payment monitoring workbook could not be refreshed."
        } finally {
            job.googleToken = null
        }
    }

    @Synchronized
    private fun replaceAll(items: List<PaymentMonitoringWorkbookSource.Item>): Snapshot {
        val snapshot = repository.replaceAll(items, LocalDateTime.now()).toSnapshot()
        cached = CachedSnapshot(snapshot, now().plus(CACHE_TTL))
        return snapshot
    }

    private fun checkCancelled(job: RefreshJob) {
        if (job.cancelRequested) throw RefreshCancelled()
    }

    private class RefreshJob(val id: String, val uploadedWorkbook: ByteArray?, var googleToken: String?) {
        @Volatile var status = "RUNNING"
        @Volatile var progress = 5
        @Volatile var phase = "downloading"
        @Volatile var importedCount: Int? = null
        @Volatile var error: String? = null
        @Volatile var cancelRequested = false
        fun snapshot() = RefreshStatus(id, status, progress, phase, importedCount, error)
    }

    private class RefreshCancelled : RuntimeException()

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
