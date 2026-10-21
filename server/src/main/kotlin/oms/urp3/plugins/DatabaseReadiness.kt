package oms.urp3.plugins

import com.zaxxer.hikari.HikariDataSource
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the application-owned database pool for the readiness endpoint.
 *
 * Liveness must remain independent from the database: a process can be alive
 * while it is temporarily unable to serve database-backed requests. This
 * registry intentionally exposes only a boolean and never connection details.
 */
internal object DatabaseReadiness {
    private val dataSource = AtomicReference<HikariDataSource?>(null)

    fun register(source: HikariDataSource) {
        dataSource.set(source)
    }

    fun unregister(source: HikariDataSource) {
        dataSource.compareAndSet(source, null)
    }

    fun isReady(): Boolean {
        val source = dataSource.get() ?: return false
        return runCatching {
            source.connection.use { connection -> connection.isValid(2) }
        }.getOrDefault(false)
    }
}
