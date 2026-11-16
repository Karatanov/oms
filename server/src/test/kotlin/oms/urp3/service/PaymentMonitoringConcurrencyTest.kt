package oms.urp3.service

import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import oms.urp3.repository.PaymentMonitoringRepository
import oms.urp3.repository.PaymentMonitoringSnapshot
import org.junit.Test
import kotlin.test.*

class PaymentMonitoringConcurrencyTest {
    private fun item(code: String) = PaymentMonitoringWorkbookSource.Item(code, null, null, null, null,
        100.0, null, null, null, null)
    private val before = PaymentMonitoringSnapshot(LocalDateTime.of(2026, 1, 1, 0, 0), listOf(item("BEFORE")))
    private val incoming = PaymentMonitoringWorkbookSource.WorkbookData(listOf(item("AFTER")), emptyList(), 123.45)

    private class Repository(initial: PaymentMonitoringSnapshot) : PaymentMonitoringRepository {
        @Volatile var persisted = initial
        @Volatile var replacements = 0
        var beforeRead: () -> Unit = {}
        var beforeWrite: () -> Unit = {}
        override fun findSnapshot(): PaymentMonitoringSnapshot {
            beforeRead()
            return persisted
        }
        override fun replaceAll(items: List<PaymentMonitoringWorkbookSource.Item>,
                                transactions: List<PaymentMonitoringWorkbookSource.Transaction>,
                                dashboardAmountPaidTotalUah: Double?, updatedAt: LocalDateTime): PaymentMonitoringSnapshot {
            beforeWrite()
            replacements++
            return PaymentMonitoringSnapshot(updatedAt, items, transactions, dashboardAmountPaidTotalUah).also { persisted = it }
        }
    }

    @Test(timeout = 10000) fun `cached reads remain available while a workbook is being parsed`() {
        val repository = Repository(before)
        val parsing = CountDownLatch(1)
        val finishParsing = CountDownLatch(1)
        val service = PaymentMonitoringService(repository, workbookParser = {
            parsing.countDown()
            check(finishParsing.await(8, TimeUnit.SECONDS))
            incoming
        })
        assertEquals("BEFORE", service.currentIfAvailable()!!.items.single().code)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val importing = pool.submit<PaymentMonitoringService.Snapshot> { service.importWorkbook(byteArrayOf(1)) }
            assertTrue(parsing.await(2, TimeUnit.SECONDS))
            val reading = pool.submit<PaymentMonitoringService.Snapshot?> { service.currentIfAvailable() }
            assertEquals("BEFORE", reading.get(2, TimeUnit.SECONDS)!!.items.single().code)
            assertEquals(0, repository.replacements)
            finishParsing.countDown()
            assertEquals("AFTER", importing.get(2, TimeUnit.SECONDS).items.single().code)
            assertEquals(123.45, service.currentIfAvailable()!!.dashboardAmountPaidTotalUah)
            assertEquals(1, repository.replacements)
        } finally {
            finishParsing.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 10000) fun `cancellation while replacement waits for the snapshot lock preserves old data`() {
        val repository = Repository(before)
        val reading = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        repository.beforeRead = {
            reading.countDown()
            check(finishRead.await(8, TimeUnit.SECONDS))
        }
        val service = PaymentMonitoringService(repository, workbookParser = { incoming })
        val pool = Executors.newSingleThreadExecutor()
        var id: String? = null
        try {
            val reader = pool.submit<PaymentMonitoringService.Snapshot?> { service.currentIfAvailable() }
            assertTrue(reading.await(2, TimeUnit.SECONDS))
            id = service.startWorkbookRefresh(byteArrayOf(1)).id
            await { service.getRefresh(id)!!.phase == "replacing" }
            assertEquals("CANCELLING", service.cancelRefresh(id)!!.status)
            finishRead.countDown()
            reader.get(2, TimeUnit.SECONDS)
            await { service.getRefresh(id)!!.status !in setOf("RUNNING", "CANCELLING") }
            assertEquals("CANCELLED", service.getRefresh(id)!!.status)
            assertEquals(0, repository.replacements)
            assertEquals("BEFORE", service.currentIfAvailable()!!.items.single().code)
        } finally {
            id?.let { service.cancelRefresh(it) }
            finishRead.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test fun `parse failure leaves the last cached and persisted snapshot untouched`() {
        val repository = Repository(before)
        val service = PaymentMonitoringService(repository, workbookParser = { error("Bad workbook") })
        val cached = service.currentIfAvailable()
        assertFailsWith<IllegalStateException> { service.importWorkbook(byteArrayOf(1)) }
        assertSame(cached, service.currentIfAvailable())
        assertSame(before, repository.persisted)
        assertEquals(0, repository.replacements)
    }

    @Test(timeout = 10000) fun `parallel imports serialize parsing without acquiring the snapshot read lock`() {
        val repository = Repository(before)
        val firstParsing = CountDownLatch(1)
        val finishFirst = CountDownLatch(1)
        val parserCalls = AtomicInteger()
        val secondThread = AtomicReference<Thread>()
        val service = PaymentMonitoringService(repository, workbookParser = {
            if (parserCalls.incrementAndGet() == 1) {
                firstParsing.countDown()
                check(finishFirst.await(8, TimeUnit.SECONDS))
            }
            incoming
        })
        service.currentIfAvailable()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<PaymentMonitoringService.Snapshot> { service.importWorkbook(byteArrayOf(1)) }
            assertTrue(firstParsing.await(2, TimeUnit.SECONDS))
            val second = pool.submit<PaymentMonitoringService.Snapshot> {
                secondThread.set(Thread.currentThread())
                service.importWorkbook(byteArrayOf(2))
            }
            await { secondThread.get()?.state == Thread.State.BLOCKED }
            assertEquals(1, parserCalls.get())
            assertEquals("BEFORE", service.currentIfAvailable()!!.items.single().code)
            finishFirst.countDown()
            first.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertEquals(2, parserCalls.get())
            assertEquals(2, repository.replacements)
        } finally {
            finishFirst.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS))
        }
    }

    @Test fun `repository failure does not publish an uncommitted snapshot`() {
        val repository = Repository(before)
        repository.beforeWrite = { error("Transaction failed") }
        val service = PaymentMonitoringService(repository, workbookParser = { incoming })
        val cached = service.currentIfAvailable()
        assertFailsWith<IllegalStateException> { service.importWorkbook(byteArrayOf(1)) }
        assertSame(cached, service.currentIfAvailable())
        assertSame(before, repository.persisted)
        assertEquals(0, repository.replacements)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue(condition(), "Timed out waiting for the controlled refresh phase")
    }
}
