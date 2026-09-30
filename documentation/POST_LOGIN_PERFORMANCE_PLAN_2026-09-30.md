# Post-login performance plan

## Observed critical path
The production image deliberately ships the Kotlin/JS **development** webpack bundle. The lightweight login page then navigates to `/app.html`, so the large application bundle starts downloading/parsing only after authentication. At the same time Dashboard immediately performs a database-backed overview request. The dashboard previously retried a failed/slow request up to four times, which could multiply backend latency.

## Implemented in this branch
1. **Preload the application bundle while the user is on the login page.** The browser can download `/composeApp.js` while credentials are being entered; navigation to `/app.html` can reuse the HTTP cache.
2. **Remove automatic serial dashboard retries on the always-on VPS.** A slow request is no longer repeated four times. The existing explicit Retry UI remains.
3. **Push dashboard scoping into SQL** for inspection reports, monitoring details and project amounts instead of loading unrelated rows and filtering/materializing them in the JVM.

These changes preserve the API and data model.

## Measurement gate
Measure separately, before and after deployment:
- login POST TTFB;
- `composeApp.js` compressed transfer size and download time;
- JS parse/execute time;
- `GET /api/v1/dashboard/overview` TTFB and total time;
- browser time from successful login response to first interactive dashboard frame;
- server-side SQL timings for the dashboard query groups.

Do not call the optimization complete until production measurements identify the remaining dominant stage.

## Next architecture options

### A. Keep Compose/Kotlin JS, optimize delivery
Lowest migration risk. Produce a genuinely optimized/minified production browser bundle in CI (not on the VPS), add immutable fingerprinted asset names and long-lived cache headers, and investigate webpack/Kotlin code splitting. This should be attempted first if production bundle generation can be made reliable.

### B. Split the initial shell from the heavy SPA
Serve a small HTML/JS authenticated shell/dashboard immediately after login and lazy-load the Compose application only when richer screens are opened. This changes frontend composition but keeps Ktor and all APIs. It gives a small critical path even if the Compose bundle remains large.

### C. Replace the web frontend with a conventional code-split SPA
Retain Ktor + MySQL as the backend/API and progressively replace the Compose web client with React/Vue/Svelte (or another browser-first framework) using route-level code splitting. This is the largest rewrite, but gives direct control over bundle budgets, lazy loading, browser caching and Web Vitals. Migrate screen-by-screen behind the same API rather than rewriting backend and frontend simultaneously.

## Recommended sequence
Deploy and measure this branch first. If bundle transfer/parse dominates, pursue A; if Compose still cannot meet the startup budget, use B as an intermediate architecture and C only as a controlled progressive migration.

Suggested acceptance target for the production VPS: authenticated workspace visible quickly, with the dashboard skeleton interactive within ~2 s on a normal broadband connection and useful dashboard data within ~3–5 s under representative production data. Treat these as engineering targets, not measured results.
