package oms.umitaf

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import oms.data.canReuseProjectSnapshot

class ProjectSnapshotRetryTest {
    @Test
    fun successfulSnapshotIsReusedWithoutARequest() {
        assertTrue(canReuseProjectSnapshot(force = false, loaded = true, failed = false))
    }

    @Test
    fun retryAfterFailedRefreshMustRequestDataEvenWithAnOlderSnapshot() {
        assertFalse(canReuseProjectSnapshot(force = false, loaded = true, failed = true))
        assertFalse(canReuseProjectSnapshot(force = false, loaded = false, failed = true))
    }

    @Test
    fun firstLoadAndForcedRefreshMustRequestData() {
        assertFalse(canReuseProjectSnapshot(force = false, loaded = false, failed = false))
        assertFalse(canReuseProjectSnapshot(force = true, loaded = true, failed = false))
    }
}
