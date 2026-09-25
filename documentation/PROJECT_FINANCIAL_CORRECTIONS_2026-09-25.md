# Project form and Financial Monitoring corrections

## Scope and existing fixes

The prior geocoding, shared date-picker and multi-field validation fixes are
documented in PROJECT_FORM_FIXES_2026-09-24.md and remain in place. They are not
rewritten or presented as new discoveries. Sumska 74 + City Kharkiv was resolved
by Nominatim; the original customer's failed response is unavailable, so its
exact historical cause is not proven. No region-centre/approximate fallback is
used. Normalization, URL encoding, empty responses and provider failures have
regression tests. The shared date bridge converts Compose pixels to CSS pixels
and constrains the overlay to the viewport, with scroll/resize cleanup.

## Manual GPS

- Manual mode remains the default in both Create and Edit.
- Mode control is now above the coordinate fields, with explicit UA/EN guidance.
- Both the checkbox and its label toggle modes; neither clears coordinates.
- Automatic mode disables manual fields; late cancelled results cannot replace
  manually entered coordinates. Retry is available without leaving the form.
- A half-entered coordinate pair is now a validation error, not a silently saved
  zero for the missing coordinate. Existing field highlighting/scroll handles it.
- Valid coordinates continue through the existing project request/map data path.

## Currency display

- Dashboard's EUR/UAH selector is extracted as CurrencySelector and reused in
  Financial Monitoring. Dashboard still starts in EUR and retains its existing
  precomputed UAH/EUR funding values.
- One financial-page state controls the act total, table amounts/currency column,
  amount sorting, and all four payment-purpose charts including labels/tooltips.
- Existing saved amountEurCents is authoritative for EUR. Original UAH amounts
  are authoritative for UAH. No stored financial values are rewritten.
- Existing EUR records store eurExchangeRate=1 (EUR-to-EUR), not a UAH rate.
  Their UAH display uses the existing NbuExchangeRateService on recordDate.
  GET /api/v1/exchange-rates/eur accepts optional YYYY-MM-DD date; omitting it
  preserves today's-rate behaviour for project forms. Prior-business-day logic
  and backend cache are reused. Invalid dates return a safe 400 response.
- Only missing conversions cause requests, deduplicated by date, at most four
  concurrent requests, with in-page caching and retry. Missing rates show dashes
  and suppress totals/charts rather than showing misleading partial totals.
- Both table and chart aggregation use the same minor-unit conversion function.
- The record editor and exports intentionally retain the original currency,
  explicitly labelled in UA/EN. Changing display currency does not change data.
- Backend deployment is required for historical-rate lookups. The frontend
  rejects a returned rate later than the requested date (older API behaviour).

## Files

- components/CurrencySelector.kt, data/FinancialDisplayCurrency.kt: shared selector
  and financial display amounts.
- screens/FinancialScreen.kt, MonthlyPaymentsChart.kt, AnalyticsCharts.kt:
  consistent table/total/chart display and Dashboard reuse.
- data/OmsApiClient.kt, server api/ProjectRoutes.kt: optional historical rate date.
- components/AddressCoordinatesCalculator.kt, screens/ProjectForm.kt,
  ProjectFormState.kt: explicit manual GPS UX and paired-coordinate validation.
- localization/Strings.kt: UA/EN mode, currency, missing-rate and original-value text.
- FinancialDisplayCurrencyTest.kt, ProjectFormValidationTest.kt: regressions.

## Validation limits

No production records are created or changed by testing. Browser-engine checks
are Chromium/WebKit, not real Safari on macOS/iOS. Full authenticated creation,
financial screen interaction and native OS picker appearance still require a
test account and a suitable Safari environment. CI results will be recorded below.
Render is not deployed automatically.

## Verified results — commit 87830ec

- Live geocoder recheck: `Sumska 74, Kharkiv, Україна` returned
  `50.0058253, 36.2367038`, identifying building 74 on Sumska street.
- [Backend regression tests](https://github.com/Karatanov/oms/actions/runs/36170019390): passed.
- [Kotlin/JS and browser regressions](https://github.com/Karatanov/oms/actions/runs/36170019287): passed,
  including the new financial conversion/aggregation and coordinate-pair tests.
  Actual date bridge passed in Chromium/WebKit at scales 1 and 2 (position,
  bounds, selection, fallback and closing). Authentication checks remained green
  in Chromium/WebKit/Firefox.
- [Pages build/deployment](https://github.com/Karatanov/oms/actions/runs/36170019281)
  and [container build](https://github.com/Karatanov/oms/actions/runs/36170019410): passed.
- Static regex, cursor, clipboard and photo-association checks passed.
- These unit/engine checks do not remove the full authenticated UI/real Safari
  verification limitations listed above. No production data was modified.
