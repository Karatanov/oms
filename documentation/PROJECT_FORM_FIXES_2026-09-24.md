# Project form corrections

## Audit checkpoint

The performance audit is paused at commit 38676b8. Completed batches and evidence
are in PERFORMANCE_AUDIT_2026-09-23.md. Remaining: representative API load,
browser profiling, concurrent SIR/database/file lifecycle, large-image/workbook
memory, remaining query/pagination/index work. Resume by reviewing subsequent
changes and rerunning affected baselines, not restarting completed work blindly.

## Customer address

Direct Nominatim request for `Sumska 74, Kharkiv, Україна` returned HTTP 200 and
latitude 50.0058253 / longitude 36.2367038 (74, Сумська вулиця, Харків).
The address exists at the provider. The customer's original production response
and error logs are unavailable, so its exact failure is not established.
Confirmed implementation weaknesses: duplicated locality/country components,
no HTTP deadlines, blocking provider I/O on the route dispatcher, and identical
UI messages for empty results and upstream/network failures. Address composition
now normalizes whitespace and deduplicates components, while retaining street
and building data. UTF-8 URL encoding is regression-tested. Provider failures
and no-result outcomes have distinct localized messages. No approximate city
fallback is silently substituted. Disabling automatic coordinates preserves
manual entry and discards late geocoder results.

## Calendar

Compose layout pixels were passed directly to a fixed CSS overlay, unlike other
native overlays which divide by LocalDensity. This misplaced calendars on
scaled/high-DPI displays. The common date component now supplies CSS pixels.
The native date input stays usable when showPicker is unavailable/rejected,
has an explicit top layer, is constrained to the viewport and closes on
scroll/resize/Escape. Native calendar placement itself is browser/OS controlled.

## Creation/edit validation

The old validator returned only the first generic error, without field markers.
It also checked hidden legacy contract dates while submitting the visible
construction contract fields. Incomplete manual dates silently retained the
previous valid value, and date strings were not checked against the calendar.
Validation now collects errors for required fields, parent, monetary groups,
coordinate ranges, real dates/order and database string-length limits. Each
invalid form group is outlined with a localized message; submission scrolls to
the first registered invalid field. Date order uses the submitted visible fields.
Backend exception text is not shown. A failed list refresh after a successful
save no longer prevents navigation away from the form.

These are reproduced code defects, not proof of the customer's exact creation
failure: their full input/payload and response were not supplied. No production
records have been created for testing. End-to-end creation against the deployed
database remains to be verified.

## Verification scope

Added backend geocoder HTTP fixture tests and frontend validation/payload tests.
Added Chrome Headless Kotlin tests and Chromium/WebKit date-bridge checks in CI.
WebKit engine coverage is not a claim of testing Safari on macOS/iOS.
Desktop browser automation initialization failed with `failed to write kernel
assets` / OS error 3. Real Safari UI and full Create/Edit browser flows remain
unverified until an appropriate browser environment is available.
Render deployment is not authorized by this task and was not triggered.

### Recorded CI results

Commit `3ecddc0`:
- Backend regression tests: run `36020134110`, passed (including four geocoder tests).
- Production frontend/Pages: run `36020134126`, passed.
- Form regression tests: run `36020134225`, passed. All 12 Kotlin browser tests
  passed in ChromeHeadless 153, with no failures or skips. Four new tests cover
  valid serialized creation payload/manual GPS, simultaneous invalid fields,
  visible contract date ordering, calendar validity and UA/EN messages.
- Actual date bridge passed in Chromium and WebKit at device scales 1 and 2:
  anchor coordinates, selection callback, viewport-edge bounds, no-showPicker
  fallback, Escape and scroll cleanup. Native OS calendar chrome itself is not
  observable in this headless bridge test.
- Existing regex, clipboard and cursor static checks passed.

Still unverified: authenticated end-to-end Create/Edit against the application
database, rendered field highlighting/scroll in the complete Compose form,
and real Safari on macOS/iOS. Successful payload/unit tests are not presented
as successful production creation. No customer records were changed.

## Changed files

- `server/.../service/GeocodingService.kt`: normalization, deadlines, coordinate validation.
- `server/.../api/ProjectRoutes.kt`: provider calls on the IO dispatcher.
- `composeApp/.../components/AddressCoordinatesCalculator.kt`: distinct messages, late-result guard.
- `composeApp/.../components/OmsDateField.kt` and `webMain/resources/index.html`: shared calendar bridge and date input.
- `composeApp/.../screens/ProjectFormState.kt` and `ProjectForm.kt`: validation and field-level feedback.
- `composeApp/.../screens/CreateProjectScreen.kt` and `EditProjectScreen.kt`: validation and safe save feedback.
- `composeApp/.../data/OmsApiClient.kt` and `localization/Strings.kt`: geocode error classification and UA/EN text.
- `GeocodingServiceTest.kt`, `ProjectFormValidationTest.kt`, `tools/test-date-picker.cjs`
  and `.github/workflows/project-form-tests.yml`: regressions and browser-engine coverage.
