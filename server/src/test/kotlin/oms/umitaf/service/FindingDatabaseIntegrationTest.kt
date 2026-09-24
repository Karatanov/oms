package oms.umitaf.service

import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.Assume.assumeTrue
import org.jetbrains.exposed.v1.jdbc.Database
import oms.umitaf.repository.ExposedInspectionFindingRepository

/** Real MySQL coverage using a disposable CI database, never application credentials. */
class FindingDatabaseIntegrationTest {
    @Test fun `scoped reads and concurrent replacement preserve ownership and manual findings`() {
        val url = System.getenv("OMS_TEST_DATABASE_URL")
        assumeTrue("Requires the disposable MySQL test service", url != null)
        require(url!!.startsWith("jdbc:mysql://127.0.0.1:3306/oms_performance_test?"))
        DriverManager.getConnection(url, "root", "").use { connection ->
            connection.createStatement().use { sql ->
                sql.execute("CREATE TABLE inspection_reports (id BIGINT PRIMARY KEY, uuid VARCHAR(36), project_id BIGINT, inspection_date DATE, summary TEXT, created_by BIGINT, report_code VARCHAR(100), inspection_type VARCHAR(20), status VARCHAR(20), rejection_reason TEXT, submitted_at DATETIME, reviewed_at DATETIME, latitude DECIMAL(10,7), longitude DECIMAL(10,7))")
                sql.execute("CREATE TABLE inspection_findings (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) UNIQUE NOT NULL, inspection_report_id BIGINT NOT NULL, category VARCHAR(50), severity VARCHAR(20), description TEXT, recommendation TEXT, is_resolved BOOLEAN, FOREIGN KEY (inspection_report_id) REFERENCES inspection_reports(id))")
                sql.execute("INSERT INTO inspection_reports(id) VALUES(1),(2)")
            }
            connection.prepareStatement("INSERT INTO inspection_findings(uuid,inspection_report_id,category,severity,description,is_resolved) VALUES(?,2,'manual','medium','unrelated',false)").use { insert ->
                repeat(20_000) { insert.setString(1, UUID.randomUUID().toString()); insert.addBatch() }
                insert.executeBatch()
            }
            Database.connect(url, driver = "com.mysql.cj.jdbc.Driver", user = "root", password = "")
            val repository = ExposedInspectionFindingRepository()
            val service = InspectionFindingService(repository)
            val manual = service.createFinding(1, "manual", "medium", "Keep this finding", null)
            assertNull(repository.findByUuid(2, manual.uuid.toString()))
            assertEquals(listOf(manual), repository.findByInspectionReportId(1))

            // Same real MySQL dataset, comparing former client-side filtering
            // to the fixed repository query. No production timings are claimed.
            fun timings(fetch: () -> Int): List<Double> {
                repeat(3) { assertEquals(1, fetch()) }
                return List(30) {
                    val start = System.nanoTime()
                    assertEquals(1, fetch())
                    (System.nanoTime() - start) / 1_000_000.0
                }.sorted()
            }
            val before = timings {
                connection.createStatement().use { sql ->
                    sql.executeQuery("SELECT * FROM inspection_findings").use { rows ->
                        var found = 0
                        while (rows.next()) if (rows.getLong("inspection_report_id") == 1L) found++
                        found
                    }
                }
            }
            val after = timings { repository.findByInspectionReportId(1).size }
            val measurements = StringBuilder("MySQL 8.4; 20001 rows; 30 measured runs after 3 warm-ups; local CI, not Render.\n")
            fun report(label: String, values: List<Double>) {
                measurements.appendLine("MYSQL_SCOPED_READ $label mean=${values.average()} p50=${values[14]} p95=${values[28]} p99=${values[29]} ms")
            }
            report("before", before)
            report("after", after)
            connection.createStatement().use { sql ->
                sql.executeQuery("EXPLAIN SELECT * FROM inspection_findings WHERE inspection_report_id=1").use { plan ->
                    assertTrue(plan.next())
                    assertNotNull(plan.getString("key"))
                    measurements.appendLine("MYSQL_PLAN key=${plan.getString("key")} rows=${plan.getLong("rows")}")
                }
            }

            val pool = Executors.newFixedThreadPool(8)
            try {
                val tasks = (1..24).map { version -> Callable {
                    service.replaceCategory(1, "hse_sir_auto", "medium",
                        (1..6).map { "$version:issue-$it" to null })
                } }
                pool.invokeAll(tasks, 60, TimeUnit.SECONDS).forEach { it.get() }
            } finally { pool.shutdownNow() }
            val final = repository.findByInspectionReportId(1)
            assertEquals(manual, final.single { it.category == "manual" })
            val automatic = final.filter { it.category == "hse_sir_auto" }
            assertEquals(6, automatic.size)
            assertEquals(1, automatic.map { it.description.substringBefore(':') }.toSet().size)
            assertFailsWith<IllegalArgumentException> {
                service.replaceCategory(1, "hse_sir_auto", "medium", listOf("valid" to null, " " to null))
            }
            assertEquals(final.toSet(), repository.findByInspectionReportId(1).toSet())
            service.replaceCategory(1, "hse_sir_auto", "medium", emptyList())
            assertEquals(listOf(manual), repository.findByInspectionReportId(1))
            assertEquals(20_000, repository.findByInspectionReportId(2).size)
            // Concurrent uploads must not bypass the per-report cap or all become main.
            connection.createStatement().use { sql ->
                sql.execute("CREATE TABLE inspection_photos (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid VARCHAR(36) UNIQUE NOT NULL, inspection_report_id BIGINT NOT NULL, original_name VARCHAR(255), storage_path VARCHAR(500), thumbnail_path VARCHAR(500), content_type VARCHAR(100), file_size_bytes BIGINT, is_main BOOLEAN, FOREIGN KEY (inspection_report_id) REFERENCES inspection_reports(id))")
            }
            val photos = oms.umitaf.repository.ExposedInspectionPhotoRepository()
            val uploadPool = Executors.newFixedThreadPool(8)
            try {
                val results = uploadPool.invokeAll((1..40).map { number -> Callable {
                    try {
                        photos.create(oms.umitaf.domain.InspectionPhoto(0, UUID.randomUUID(), 1,
                            "$number.jpg", "test-original", "test-thumbnail", "image/jpeg", 100, true))
                        true
                    } catch (exception: IllegalArgumentException) {
                        assertEquals("An inspection report can contain no more than 30 photos.", exception.message)
                        false
                    }
                } }, 60, TimeUnit.SECONDS).map { it.get() }
                assertEquals(30, results.count { it })
                assertEquals(30, photos.list(1).size)
                assertEquals(1, photos.list(1).count { it.isMain })
                assertTrue(photos.list(2).isEmpty())
            } finally { uploadPool.shutdownNow() }
            val output = java.io.File("build/reports/performance/mysql-scoped-reads.txt")
            output.parentFile.mkdirs()
            output.writeText(measurements.toString())
        }
    }
}
