package oms.urp3

import oms.data.ProjectSnapshotState
import kotlin.test.*

class ProjectSnapshotStateTest {
    @Test fun `ordinary navigation reuses a successful result`() {
        val state = ProjectSnapshotState()
        assertTrue(state.accept(assertNotNull(state.beginRefresh(false, false))))
        assertNull(state.beginRefresh(false, false))
    }

    @Test fun `successful import invalidates the old snapshot and requires a new request`() {
        val state = ProjectSnapshotState()
        val old = assertNotNull(state.beginRefresh(false, false))
        assertTrue(state.accept(old))
        state.invalidate()
        val next = assertNotNull(state.beginRefresh(false, false))
        assertNotEquals(old, next)
        assertTrue(state.accept(next))
        assertNull(state.beginRefresh(false, false))
    }

    @Test fun `cancelled forced refresh cannot reuse the previous result as fresh`() {
        val state = ProjectSnapshotState()
        state.accept(assertNotNull(state.beginRefresh(false, false)))
        val cancelled = assertNotNull(state.beginRefresh(true, false))
        val retry = assertNotNull(state.beginRefresh(false, false))
        assertNotEquals(cancelled, retry)
        assertFalse(state.accept(cancelled))
        assertTrue(state.accept(retry))
    }

    @Test fun `invalidation during a request rejects its old response and error`() {
        val state = ProjectSnapshotState()
        val old = assertNotNull(state.beginRefresh(false, false))
        state.invalidate()
        assertFalse(state.accept(old))
        assertFalse(state.isCurrent(old))
        assertNotNull(state.beginRefresh(false, false))
    }

    @Test fun `clear followed by a new session cannot accept an older response`() {
        val state = ProjectSnapshotState()
        val old = assertNotNull(state.beginRefresh(false, false))
        state.invalidate()
        val newSession = assertNotNull(state.beginRefresh(false, false))
        assertTrue(state.accept(newSession))
        assertFalse(state.accept(old))
        assertNull(state.beginRefresh(false, false))
    }

    @Test fun `a reported refresh error requires retry even after a successful cache fill`() {
        val state = ProjectSnapshotState()
        state.accept(assertNotNull(state.beginRefresh(false, false)))
        assertNotNull(state.beginRefresh(false, true))
    }
}
