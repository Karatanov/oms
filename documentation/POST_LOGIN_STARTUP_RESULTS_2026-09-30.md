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

A follow-up comparison with GitHub Pages found a second deployment-wide
regression. `composeApp/webpack.config.d/render-no-minify.js`, added for an old
Render memory limit, disabled Terser for every production Webpack build. The
Pages CI log therefore reported a 20.5 MiB `composeApp.js` without the
`[minimized]` marker. Render now redirects its frontend to those same Pages
assets, so the mistake affected both URLs. Pages also continued serving the
Compose application as its root page instead of the lightweight login page.

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
7. Remove the global no-minify override. It was named for Render but silently
   affected GitHub Pages as well.
8. Publish `login.html` as the Pages entry point and the Compose application as
   `app.html`. The login page does not preload Compose and sends authentication
   directly to the Render API when hosted on `karatanov.github.io`.

## Validation

The isolated Chromium regression test renders useful authenticated dashboard
cards and verifies one-time bootstrap consumption in 1.23 seconds total on the
local test host, below the two-second shell target.

All pull-request workflows passed, including backend regression tests, project
form/browser authentication tests, and a complete Linux production image build.
Production Webpack emitted a 6,103,878-byte fingerprinted JavaScript asset. The
image build produced these exact transfer variants:

| Asset | Uncompressed | Gzip | Brotli |
|---|---:|---:|---:|
| `composeApp.3a9dc4fde1c5.js` | 6,103,878 B | 1,583,397 B | 1,249,379 B |
| Skiko WASM | 8,641,646 B | 3,280,102 B | 2,971,443 B |

The immutable PR image was extracted in CI and served from an isolated,
loopback-only staging container on the production VPS. The same fresh-profile
Chrome CDP script, with cache disabled, measured:

| Event | Baseline production | Optimized staging |
|---|---:|---:|
| JavaScript complete | 39,117 ms | 24,279 ms |
| WASM complete | 59,614 ms | 42,147 ms |
| JS + WASM compressed bytes | 8,269,898 B | 4,220,822 B |

That is a 49% reduction in mandatory compressed asset bytes and a 29% reduction
in the complete Compose asset path on this client route. The staging path used
an SSH tunnel and HTTP/1.1, adding 1.5–2.8 seconds even to tiny HTML/API
requests; it is therefore a conservative transport comparison, not a claim
about direct production HTTPS latency.

With a deterministic dashboard fixture, the staged shell was visible at
6,671 ms and populated at 9,457 ms from guest authentication. Those absolute
values are dominated by the SSH tunnel (the fixture itself took 2,786 ms across
the tunnel and about 1 ms on the VPS). The isolated browser regression is the
valid shell-render measurement: 1.23 seconds. A direct authenticated production
measurement should be repeated after an approved deployment.

Production is intentionally unchanged by this branch. Final cold-cache asset
sizes and the full Compose asset path were captured from the built PR image in
staging using the same measurement script. The temporary staging container,
files, and SSH tunnel were removed after measurement.

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
