# Navigation loading

`App` owns session restoration/startup. It dismisses the startup HTML overlay
after the real Compose shell (or login) has had a frame to paint. Consuming
bootstrapped dashboard data and arbitrary HTTP responses cannot dismiss startup.

`AppLayout` keeps its real `Sidebar` outside `RouteContentHost`. Each navigation
revision captures immutable screen parameters. The host retains the last
successful keyed composition, mounts only one candidate, and promotes that same
composition after the screen reports essential initial data readiness. Disposed
candidate results cannot complete a newer candidate. Returning to the retained
route cancels the transition without discarding its form state.

The 180 ms delay controls only the visibility of the indicator. Input is blocked
immediately in the content rectangle, including keyboard focus and native panes.
On failure the old composition remains mounted, with a localized retry action.
Retries use a new candidate identity. Browser history restoration uses the same
navigation revision mechanism.

The loading indicator is a small native overlay positioned by the actual Compose
content bounds, because Leaflet and photo panes live above the canvas. It is not
an alternate application shell. Pending screens cannot mount native panes or
register the floating table scrollbar. The sidebar is neither recreated nor
covered. Native panes remain inert while the transition is pending.

`OmsApiClient` no longer controls presentation. Screens report readiness through
`ReportRouteReadiness`, based on their essential initial data only. Photos,
analytics, activity feeds, exchange rates, and subsequent widget refreshes use
local state. Legacy native upload/import helpers show a non-blocking progress
notice rather than reusing the full-screen startup element.

## Verification

- `node tools/test-startup-shell.cjs`: bootstrap consumption cannot hide startup;
  the explicit real-shell handoff does.
- `node tools/test-route-overlay.cjs`: content bounds, hidden short-request
  indicator, Ukrainian/English messages, retry, inert native panes, operation
  progress independent of startup.
- `OMS_SMOKE_ASSETS=build/pages node tools/test-route-transitions.cjs`: real
  production Compose bundle against read-only fixture APIs; slow transition,
  retained pixels, error/retry, superseded requests, browser Back/Forward, fast
  transitions, and non-blocking analytics.
- Existing Kotlin form tests and browser auth/date/login tests remain enabled.

The production Pages workflow runs the real-app test before deployment. The
test fixture never uses production credentials, data, or write endpoints.
