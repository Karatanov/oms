# UI/UX redesign validation — 27–28 August 2026

## Environment and scope

Validation has three distinct layers: compiled unit tests, local Docker/MySQL API integration checks on 27 August, and actual compiled UI interaction checks on 28 August using loopback-only synthetic HTTP fixtures. The latter do not validate database persistence. Existing customer project records and the Render deployment were not edited during testing. No production credentials or fixture servers are stored in source.

## Automated build/tests

- Docker: `gradle :server:installDist :server:test --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process` completed successfully (production Webpack/Binaryen bundle and server distribution).
- Five JVM tests passed: root/static application response, JWT issue/verification and tamper rejection, inspection submit/approve workflow, invalid transitions/rejection reason.
- Four compiled Kotlin/Wasm tests passed in the in-app browser: corporate Primary unchanged, exact euro-cent formatting including large and negative amounts, localized months, required UK/EN control strings.
- Node-only execution of Skiko tests is unsupported in this dependency version. The browser runner executes the same Kotlin test exports. Reproduce by compiling/syncing `:composeApp:compileTestDevelopmentExecutableKotlinWasmJs` and `:composeApp:wasmJsTestTestDevelopmentExecutableCompileSync`, running `python tools/serve-web-tests.py`, then opening its local URL in a browser. Expected result: `4 passed, 0 failed`.

## API integration checks

The local running app passed 22 checks: dashboard, project registry/map/details, report registry, financial registry, procurement, users, roles and documents; report edit and move/restore; generated SIR workbook download; PNG upload and thumbnail download; anonymous project registry/map/general details; denial of anonymous access to users, financials, reports and procurement.

The inspection repository's new author join returned the actual QA creator username. Generated XLSX download returned 4,091 bytes; generated PNG thumbnail returned 908 bytes. These were fixture files, not customer documents.

## Browser review

English/Ukrainian login switching, a 768 px login layout, password-reset dialog opening and Escape dismissal were verified against the rendered production bundle.

The following checks used the compiled application with synthetic HTTP fixtures, at 1280, 1024 and/or 768 px viewport widths:

- Navigation between dashboard, projects, map, reports, documents, finance, procurement and administration. The compact sidebar remains usable at narrower widths.
- Dashboard two-column analytics, white chart surfaces, localized month labels and centered year grouping.
- Project hierarchy expansion to the third level, sector filtering, create-project form scrolling and Cancel.
- Map overview covering Ukraine, centered cluster count, object-type filtering and marker-count updates.
- Inspection edit dialog, inspection-type picker, Escape dismissal of only the picker, move-report target selection and Cancel.
- Manual SIR project/subproject/part selection, inspection-type options and long-form scrolling. The native OS date popup was not capturable in the browser screenshot; its visual positioning is not claimed as verified.
- Administration UK/EN switching, create/edit light dialogs, Cancel, and an intentionally injected HTTP 422 response shown inside the user editor. No user was created or password changed in this UI pass.
- Procurement editor, all six localized status choices, picker Escape and editor Cancel after scrolling.
- Finance editor, payment-purpose selection, Cancel after scrolling, horizontal table controls, and import/export dialog opening/Cancel.
- Document upload dialog opening/Cancel; empty-document state and Other filter are present.
- Final production rebuild passed on Windows (10m 17s, five JVM tests with no failures). A fresh-origin smoke check confirmed dashboard-to-documents navigation, the localized document-type picker including subproject parts/invoice/act, picker Escape and dialog Cancel with no browser console errors. A normal reload of the earlier local fixture had reused cached scripts, so it was not used as evidence for the final bundle.
- Guest entry exposes only projects and map; mutation buttons are absent from the public registry.

A reproducible native Material tooltip crash (`layouts are not part of the same hierarchy`) was found during browser testing. Replacing popup tooltips with the shared in-canvas layer restored navigation; no new console errors occurred during the subsequent screen pass.

## Build/runtime notes

- Windows Java initially failed creating a Unix-domain loopback connection. A command-scoped `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<nonexistent-path-under-build>` allowed the JDK to fall back to TCP. The directory must remain nonexistent. Production compilation, bundling, server distribution and JVM tests then passed on Windows Java 21 without changing application configuration.
- Docker Desktop subsequently failed before engine startup: `listening on .../dockerInference: The file cannot be accessed by the system`. The CLI was found and invoked by absolute path under the configured Docker Desktop installation, and startup was retried. This is a daemon/filesystem failure, not a missing PATH entry. Consequently the final UI pass used synthetic HTTP fixtures and the 22 database integration checks were not repeated on 28 August. Cleanup of earlier isolated Docker QA records could not be confirmed while the engine was unavailable.
- Production bundling reports the existing Skiko/application Wasm asset-size warnings and a dynamic-import warning. These are warnings, not compilation failures.
- Local API URLs now preserve the serving origin for packaged deployments, including mapped Docker ports and `127.0.0.1`. The standalone development UI on port 8081 targets the same hostname on port 8080. Render remains same-origin.

## Remaining validation boundaries

The final UI pass does not establish production end-to-end CRUD, native file-picker/calendar behavior, customer-photo rendering or successful Render deployment. The approved-funding endpoint still supplies UAH and uploader metadata is absent from the document/financial APIs; the UI labels the currency honestly and displays an em dash for unavailable authors. See the design-system companion for implementation details.
