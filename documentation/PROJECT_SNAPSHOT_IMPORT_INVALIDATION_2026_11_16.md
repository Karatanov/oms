# Project snapshot invalidation after imports — 2026-11-16

## Verified failure path

The server projects endpoint calculates disbursements from the current payment
snapshot. The browser, however, keeps mapped project rows in `ProjectRepository`.
A successful financial import incremented `reloadKey`, then called the ordinary
`refresh()`; with `loaded=true` and no error this reused the old project list.
Consequently the payment register could be refreshed while project-derived map
and card cash values remained stale in that browser session.

The procurement import similarly reloaded its registry but did not invalidate
shared project context, and its project-load effect ran only once on screen
creation. This matters because that import can create/update source subprojects.

These findings trace actual control flow; no customer import or browser timing
was executed against production to demonstrate a specific stale value.

## Correction

- Successful payment and procurement imports invalidate shared project freshness
  before triggering their existing reload. Failed/cancelled imports do not.
- Invalidation preserves visible rows rather than blanking the map/table while
  replacements load. Ordinary navigation still reuses a successful fresh cache.
- Procurement's project-load effect now follows its existing `reloadKey`.
- `ProjectSnapshotState` versions requests and invalidations. Starting a forced
  refresh revokes old freshness, so cancellation cannot make the next navigation
  silently reuse the previous result.
- The repository publishes a response/error only for the current generation.
  Clearing or invalidating during a request rejects that response. An in-flight
  request from before `clear()` therefore cannot repopulate cleared rows after
  logout/session change. This is a cache-state guard, not a claim of a verified
  historical cross-session data disclosure.

The network refresh mutex remains in place. No API, schema, permission, source
formula, or database value changes are part of this correction.

## Verification and limits

Six new pure-state regressions cover successful reuse, post-import invalidation,
cancelled forced refresh, stale responses/errors, clear/new-session isolation,
and retry after an error. Three existing snapshot-policy tests are now also in
the focused runner; all nine pass. Together with the existing calculation and
service checks, all 133 focused Kotlin tests and 1,000 decimal fixtures pass.

The test target `:composeApp:compileKotlinJs --no-daemon --max-workers=1` was
attempted locally but failed before compilation with Gradle's environment error
`java.io.IOException: Unable to establish loopback connection`. No local full
Compose JS/WASM build or interactive browser verification is claimed. The pure
state tests do not by themselves verify Compose effect scheduling, UI wiring or
network cancellation; the changes to those paths are covered by source review
and the normal CI/browser regression workflow after push.

No production data changes were made. Commit, push, full CI and deployment status
are reported separately. The wider formula audit remains ongoing.
