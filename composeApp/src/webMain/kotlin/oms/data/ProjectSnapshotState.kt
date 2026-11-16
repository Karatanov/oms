package oms.data

/** Version the cache without discarding visible rows while a replacement is loading. */
internal class ProjectSnapshotState {
    private var revision = 0L
    private var loadedRevision: Long? = null

    fun beginRefresh(force: Boolean, failed: Boolean): Long? {
        if (canReuseProjectSnapshot(force, loadedRevision == revision, failed)) return null
        // A cancelled attempt must not leave its previous result marked fresh.
        revision++
        loadedRevision = null
        return revision
    }

    fun invalidate() {
        revision++
        loadedRevision = null
    }

    fun isCurrent(attempt: Long): Boolean = attempt == revision

    fun accept(attempt: Long): Boolean {
        if (!isCurrent(attempt)) return false
        loadedRevision = attempt
        return true
    }
}

/** A failed refresh must not make a navigation retry reuse the old snapshot. */
internal fun canReuseProjectSnapshot(force: Boolean, loaded: Boolean, failed: Boolean): Boolean =
    !force && loaded && !failed
