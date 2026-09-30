# Post-login startup investigation — 2026-09-30

## Reproducible baseline

The baseline was measured against `https://ua-oms.com` with a fresh headless
Chrome profile, browser cache disabled, HTTP/2 enabled, and a guest session so
no production credentials were required. The script is
`tools/measure-web-startup.mjs`.

| Event | Cold-cache time from authentication |
|---|---:|
| Authentication response | 38 ms |
| `app.html` complete | 159 ms |
| `composeApp.js` complete | 39,117 ms |
| Skiko WASM requested | 40,088 ms |
| Skiko WASM complete | 59,614 ms |
| Session API requested | 59,806 ms |
| Session API response | 59,921 ms |

Production asset measurements before this branch:

| Asset | Uncompressed | Gzip transfer | Observed client transfer |
|---|---:|---:|---:|
| `composeApp.js` development bundle | 38 MiB | 4,802,447 bytes | 28.9–39.1 s |
| Skiko WASM | 8.2 MiB | 3,467,451 bytes | 18.8–22.6 s |

The VPS itself read and served the same compressed files in 0.12 s and 0.17 s.
Authentication and session requests were 0.14–0.17 s. Existing production logs
showed the authenticated dashboard overview around 0.2 s. Browser execution
between the JS download and the WASM request was about one second.

## Root cause

The critical path was not MySQL or JVM dashboard processing. It was the browser
being forced to transfer a development Kotlin/JS bundle and then Skiko WASM
before it could issue the first session request. On the measured client route,
the 8.27 MiB compressed payload took about one minute. Preloading the same large
file could start the transfer earlier but could not remove this critical path.

Three Leaflet stylesheets were also render-blocking in `app.html` even though
Leaflet JavaScript was correctly deferred until the map screen.

`material-icons-extended` exposes a large dependency graph, but production
Kotlin/JS dead-code elimination retains the used icons. Replacing roughly 70
used icons before fixing the development artifact would add substantial risk
for a much smaller expected gain, so it is not part of this change.

## Implemented changes

1. Build `jsBrowserProductionWebpack` in the image and give CI a bounded
   45-minute timeout instead of shipping a development artifact.
2. Fingerprint `composeApp.js` during the image build, rewrite both HTML entry
   points to the generated name, and apply one-year immutable caching only to
   fingerprinted JS/WASM. HTML and unfingerprinted assets must revalidate.
3. Precompress JavaScript, CSS, and WASM with Brotli and gzip; Ktor negotiates
   Brotli first and gzip as fallback.
4. Render a browser-native authenticated dashboard shell immediately from the
   existing overview API while Compose downloads. Compose consumes that same
   JSON response once it starts, avoiding a duplicate overview query.
5. Load Leaflet CSS only when the map is first opened.
6. Add Performance API marks (`oms-shell-visible`, `oms-shell-useful`) and a
   cold-cache measurement script so network/startup regressions are separable
   from API latency.

## Validation

The isolated Chromium regression test renders useful authenticated dashboard
cards and verifies one-time bootstrap consumption in 1.23 seconds total on the
local test host, below the two-second shell target. The full Linux production
image and Kotlin/JVM tests run in pull-request CI before merge.

Production is intentionally unchanged by this branch. Final cold-cache asset
sizes and full-app timing should be captured from the built PR image in staging
before merge, using the same measurement script.

## Remaining bottleneck and next step

The Skiko WASM runtime remains a multi-megabyte mandatory dependency for the
Compose application. The lightweight shell removes it from the critical path
to useful dashboard content, while the production bundle, Brotli, and immutable
caching reduce the wait for the complete application. If the complete Compose
workspace still misses the target on slow routes, serve fingerprinted assets
through a CDN before considering a frontend rewrite.

## Rollback

Revert this branch's commits and rebuild the previous image. No database schema,
credentials, Traefik routing, upload storage, or API contract is changed.
