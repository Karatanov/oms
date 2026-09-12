# Dashboard and navigation regression — 12 September 2026

## Confirmed cause

The deployed JavaScript client threw `SyntaxError: Invalid regular expression:
/(?i)\s+(область|oblast|region)$/gu: Invalid group` in `toRegionChartLabel`,
called by `FundingByOblastChart`. This occurred while composing loaded data,
not while waiting for the Dashboard API. The failed composition left the
dashboard without charts and prevented subsequent screen recomposition,
even though navigation callbacks could still change the URL.

Kotlin/JS delegates regular expressions to JavaScript's `RegExp`. The inline
`(?i)` flag accepted by the previous runtime is not valid there. Replaced it
with `RegexOption.IGNORE_CASE` in all three affected client helpers:

- Funding chart region labels (`AnalyticsCharts.kt`).
- Unknown English region names (`UkraineRegionAutocomplete.kt`).
- English address extraction (`ProjectDetailScreen.kt`).

No database data, permissions or financial calculations changed.

## Reproducible checks

- Build the deployable JS bundle: `./gradlew -PrenderJsOnly :composeApp:jsBrowserProductionWebpack`.
- Run JS regression tests in installed Chrome: `./gradlew -PrenderJsOnly :composeApp:jsBrowserTest`.
  Set `CHROME_BIN` if Chrome is not discoverable. Tests cover region suffixes,
  mixed case, UK/EN localization, unknown regions and address extraction.
- Run `node tools/serve-dashboard-smoke.mjs` and open `http://127.0.0.1:18088/`.
  This read-only loopback fixture serves only compiled client assets and synthetic
  API responses; it does not connect to the real database or use credentials.
- Verify all four non-empty charts; UK/EN language switching; horizontal/vertical
  orientation switching; sidebar navigation to reports, subprojects, procurement,
  documents, administration and financial monitoring; return to the dashboard.
  The fixture includes 36 financial records so the financial-table heading can
  also be checked above its rows and after vertical scrolling.
  Inspect the browser console for uncaught exceptions, not just the route URL.

## Verification

Production bundling and all seven Kotlin/JS tests passed in Chrome Headless
(zero failures). An obsolete localization assertion for the intentionally removed
`workspace` caption was updated to check the current `dashboard` caption instead.
The browser smoke checks
above passed against that production bundle with no console errors. The original
exception was captured on the deployed site before applying the fix.

The local UI fixture validates rendering/navigation, not production database
contents or successful Render deployment. Deployment remains a separate step.

The server marks packaged static assets as `no-cache`: the generated client
bundle intentionally has a stable filename, so this prevents a browser from
reusing a prior interface after a Render deployment.

Windows Java's Unix-domain socket issue was bypassed only for the build process
with `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<path-to-an-existing-file>`.
An unusable socket directory causes the JDK to fall back to TCP; no persistent
Java or application setting is changed.
