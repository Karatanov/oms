# Financial workspace update

## User-visible behavior

Monetary charts initially show UAH. Supported EUR switches continue to use the existing stored conversions or validated recorded exchange rates. No new rate source or conversion rule is introduced.

Approved payment transactions enter the chart model with their original UAH amount and a frozen EUR equivalent when the workbook supplies one. UAH is never reconstructed from that EUR amount or a derived ratio; changing a chart currency reads the corresponding source amount. Legacy records retain the existing validated conversion fallback.

Filters persist only within the current application session. Browser reload and authentication lifecycle entry/exit create a fresh filter state, including table search, currency selection and pagination. Language, theme, credentials and layout preferences are untouched. Old `oms.table.v1.*` local-storage entries and the old dashboard tranche entry are no longer read.

Financial Monitoring has one searchable global Subproject selector. Dashboard drill-down uses the same `subprojectFilter` state. Changing it scopes KPIs, progress requests, monthly transactions and both source tables. Page-level reset clears the global scope. Table-level reset clears the shared local code search and pagination without changing global scope. Manual source refresh retains filters.

The single Update data action is in the page heading. Its existing permission, confirmation, source download/upload, progress and cancellation flow are preserved. It is disabled while the current refresh is running.

The lower dated table is now Payment register / Реєстр платежів. The upper cumulative table retains its previous title.

The monitoring PDF export contains both tables. The dated payment section follows the selected subproject population, code search, month, year and purpose, and includes every matching transaction in chronological order. It shows date, original project/lot code, tranche and source UAH/EUR amounts, with a payment-only total. Cumulative parent/lot indicators are not added to those totals. If an EUR value is unavailable, its total is unavailable too rather than a misleading partial sum. The second table follows the summary on the same page where space permits, with repeated section headers on continuation pages.

## Regional investment chart and data lineage

The new Financing structure by oblast grouped chart replaces the planned/paid chart and the regional execution chart. The already-authorized `/api/v1/financials/payment-monitoring` response supplies:

| Source DTO field | Meaning | Unit |
| --- | --- | --- |
| `items.totalInvestmentUah` | Total Investment Cost | UAH |
| `items.eibInvestmentUah` | Investment Cost excluding local co-financing | UAH |
| `items.localCoFinancingUah` | Local co-financing | UAH |

These are imported investment fields, not dated transaction amounts, contract execution percentages or AT planned payments. Regions come from the permitted subproject registry using the shared parent/lot code resolver and canonical oblast normalization. Aggregation selects the highest filled parent budget per column; available children contribute only when their parent value is missing. Values are summed in minor units, null is not a fabricated zero, and source reconciliation differences remain visible in tooltips. A transaction month or purpose cannot allocate a snapshot budget to a period, so those filters affect cash, not investment amounts.

The redundant dashboard-overview request and obsolete planned/actual chart implementation were removed. Existing financial-progress requests remain because construction and sector progress still use them. No API contract, authorization or database migration is changed.

## Procurement chart drill-down

Procurement-status tooltips on the Dashboard list unique subproject codes from the same authorized status population as the count. Procurement status rows and monthly contract count/value charts also show their supporting codes. Selecting a status or month replaces stale local search, oblast, subproject, method, prior month and incompatible type filters, resets pagination and scrolls to the matching procurement rows. The chart's tranche scope is retained; a Dashboard transition explicitly supplies its selected tranche, including all tranches. Lists identify unique subprojects; the bar count still counts procedures/contracts, not the number of unique subprojects.

## Semantic colors

Payment and procurement category bars use `paymentPurposeColor`, shared with the corresponding chips. Procurement-status charts share their mapping with status chips. Construction and sector progress share registry chip mappings. Investment Total/EIB/Local use distinct Information/Success/Purple design tokens. Sorting and filtering do not assign colors by array position.

## Checks

Calculation tests cover parent/lot exclusion, child fallback, subproject/region/tranche scoping, real zero, missing values, source differences and the absence of invented monthly budgets. Session and semantic-color tests run with the web regression suite. `tools/test-financial-workspace.cjs` runs the actual compiled production frontend against a local read-only fixture, verifies subproject API parameters, clearing filters and reload behavior, captures screenshots and rejects unhandled browser exceptions. This fixture is not evidence of production financial amounts.

The browser regression also selects an upload through the real file chooser, intercepts the multipart request locally, and exercises refresh progress, completion and an HTTP 503 upload failure. It checks that the selected subproject is retained, the completed job reloads the monitoring snapshot exactly once, failure leaves the snapshot unchanged, and the update confirmation can be reopened. The test payload exercises transport only; it is not an XLSX parser test and never reaches production.

The existing container workflow performs the release build and VPS health/revision checks. Deployment evidence and remaining host-specific limits must be recorded separately from local test results.
