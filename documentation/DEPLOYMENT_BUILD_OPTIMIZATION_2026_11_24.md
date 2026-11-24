# Production build optimization — 24 November 2026

## Measured baseline

Workflow `45345105175`, commit `c1451cf164744b9e3a4c39933584427e637f15ec`:

| Stage | Measured duration |
|---|---:|
| Compose production compilation/packaging | 19 min 26 sec |
| Webpack portion within that task | 17 min 27 sec |
| Docker build + GHCR push | 85 sec |
| VPS pull/switch/revision check | 56 sec |
| Full workflow | about 24 min |

The principal bottleneck was JS minification, not transferring the container to
the VPS. The separate Pages workflow compiled the same production UI again.

## Changes

- Keep Kotlin production dead-code elimination and Webpack production mode;
  use the supported `TerserPlugin.esbuildMinify` backend instead of Terser's
  expensive whole-program compression. Pin build-only `esbuild` to `0.28.2`.
  Preserve legal comments inline and target ES2015; do not ship eval/development
  bundles. Development and browser-test bundling remain unchanged.
- Cache the complete unbranded `frontend-dist` by exact frontend/shared source,
  webpack configuration, Gradle configuration and lockfile content. No fuzzy
  restore prefix is allowed. A missing cache falls back to a clean build.
  Server-only changes can reuse compiled browser assets. Release metadata is
  applied later, so a cache hit cannot report an old deployment revision.
- Pages starts after a successful container workflow and downloads its exact
  frontend artifact. It validates source repository, event, master branch and
  current commit, rejecting PR/foreign/stale artifacts. Manual dispatch supports
  a successful source run ID, but never silently publishes a different revision.
- Keep regression gates and health/revision/asset verification. Add a real
  production-bundle navigation check before container publication, including
  cache hits. Keep the existing Pages navigation check after its packaging.
- Pull the immutable image once on VPS (`up --pull never` after successful pull).
  Bound individual public health requests; preserve automatic rollback.
- Keep frontend artifacts seven days, permitting explicit Pages retries without
  another frontend compilation. No production data or credentials enter them.

## Local evidence and limits

An existing unminified production JS bundle (20,800,737 bytes) was minified in
3.8 seconds locally to 6,818,402 bytes (1,823,948 gzip bytes). This is a minifier
benchmark on an existing bundle, not a measurement of a fresh full release, and
not a size comparison with the different baseline commit's source code.

Six Node regressions cover production/development configuration, public globals,
callbacks, UA/EN text, monetary numbers, legal comments, immutable revision
packaging, renderer references and asset-manifest sizes. Actionlint validates the
three changed workflows. The actual clean Kotlin build and production browser
navigation must pass CI; Windows Gradle currently fails to establish loopback.

First CI run `45351529633` (`a81c1537`) confirmed the actual production task at
61 seconds and Webpack at 7.24 seconds. The production navigation smoke test
passed in 28 seconds. Kotlin executable outputs were restored from Gradle cache
in this run; a changed UI still needs compilation before the fast minifier.
The full existing form/mobile regression workflow remains a separate deployment
gate. Its first attempt timed out after a mobile drawer click; only that failed
job was retried, with no safety gate disabled.

Expected benefit: remove the ~17-minute minifier bottleneck on UI changes and
avoid browser recompilation entirely on backend-only changes when the exact
cache is warm. First clean/cache-miss builds still compile Kotlin. No fixed
end-to-end speedup is promised before measuring the new GitHub run.

## Recovery

If native minification regresses, revert the webpack fragment, its build-only
dependency and lockfile entries; do not disable minification or ship a debug
bundle. A normal source/config change changes the cache key. Existing immutable
container tags and VPS automatic rollback remain usable. Pages can explicitly
retry the successful matching container run while its artifact is retained.
