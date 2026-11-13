# Financial aggregation review, 10-13 November 2026

## Confirmed corrections

- Normalize source categories before aggregation, using shared Kotlin keys on
  the server and client. Previously, multiple shelter source descriptions became
  separate aggregates but were subsequently displayed as the same translated name.
- `Security Centres` and `Центри безпеки` now form one security-centres category.
- Per the customer's OMS terminology, `renovation` belongs to reconstruction.
  `Overhaul / Капітальний ремонт` remains capital repair, rather than being mapped
  to an ambiguous `renovation` key with no matching client translation.
- Financial progress prefers the exact source `constructionAmount` denominator.
  The legacy project contract attribute can contain Works + TS + CSC totals and
  therefore must not override an explicit construction-only source amount.
  Positive stored contract amount and approved budget remain fallbacks.

## Formula and reconciliation checks

- Progress is `sum(works paid UAH) / sum(execution base UAH) * 100`, not an
  unweighted average of individual ratios. W payments exclude TS/CSC payments.
- Canonical category merging must conserve both numerator and denominator.
- The source refresh independently reconciled 93 dated payment rows to
  UAH 309,119,376.61. Source E4 remains UAH 304,888,750.30, as supplied; these
  are distinct source controls, not values to force into agreement.
- Primary subproject payment summaries must not be added to their W/TS/CSC
  child component rows a second time. Existing dashboard logic already filters
  primary rows, so that logic was preserved.

## Verification and limits

### Follow-up: bounded authenticated downloads (13 November)

- Native authenticated downloads bypass Ktor's 75-second API timeout. Their
  `fetch`/body read previously had no deadline, so a stalled connection left the
  operation progress indicator active indefinitely.
- Each download now has an independent five-minute AbortController deadline
  covering both response headers and body transfer. It is cleared on every exit;
  timeout produces a UA/EN retry message and releases the progress counter.
  This is longer than the SIR server PDF converter's 90-second limit. Successful
  filenames, authorization headers, POST selection payloads and object-URL
  cleanup are preserved. No report generation or financial formulas changed.
- Seven offline tests exercise the shipped auth.js with mocked transport/clock:
  header/body stalls, UA/EN messages, successful POST, HTTP auth failure, foreign
  URL exclusion, concurrent downloads and retry after timeout. They pass and are
  included in browser-regression CI. The 42-pattern web check passes.
- The existing cookie-free auth/download/image/logout browser checks also pass
  for user and guest in locally installed Chrome. The packaged Playwright Chrome
  binary was unavailable, so Chrome was explicitly selected for that run. This
  does not certify Safari/Firefox or an authenticated production export.

### Follow-up: payment-monitoring PDF selection (13 November)

- The payment register's PDF button sent only the language. Its endpoint always
  exported the whole source in code order, ignoring the visible code search and
  selected numeric/text sort. It now sends the current search, column and
  direction, and the server applies them to the complete snapshot, not a page.
- Selection mirrors the table's case-insensitive substring match, numeric sorts
  and null/blank-last behavior in both directions. Unsupported sort parameters
  and overlong searches return HTTP 400 before retrieving the source. Existing
  language-only links retain their default full-register code sort.
- PDF filter metadata now describes an actual code search rather than falsely
  asserting a fixed code sort. Layout, monetary values, import and authorization
  are unchanged. No records are mutated during selection.
- Five standalone Kotlin/JUnit tests pass, including all eight numeric columns
  in both directions, text/compliance ordering, zero versus missing values,
  45 matching rows across pagination, empty selections and invalid parameters.
  The 42-pattern web check passes. Full builds and authenticated browser/PDF
  download verification remain required in CI/after deployment; the helper tests
  do not certify the download workflow or PDF rendering.

### Follow-up: browser-compatible regression fixtures (13 November)

- CI for `6669741` failed at `:composeApp:compileTestKotlinJs`: the newly
  added progress-loader tests imported JVM-only `runBlocking`. The preceding
  standalone JVM test success did not establish browser-test compatibility.
- Replaced it with standard-library continuations for these immediately
  completing fixtures. The runner asserts completion, so an unexpectedly
  suspended fixture cannot silently pass. Production code and dependencies
  are unchanged.
- `tools/FinancialChartStateChecks.kt` invokes the same seven test cases for
  standalone JVM/JS checks. The real production helpers and test files compiled
  with Kotlin/JS 2.3.0 and linked to CommonJS; all seven checks then passed in
  Node. This bypasses the local Gradle loopback limitation without substituting
  JavaScript reimplementations of the Kotlin logic.
- This is a JS runtime check, not a real-browser/Compose rendering test. The
  complete browser regression workflow still needs to pass after the fix.

### Follow-up: scoped progress loading and retry (13 November)

- The grouped-progress request previously retained the previous result during a
  scope change, displaying old region/subproject amounts under the new caption.
  A failed request silently removed the charts; `runCatching` also swallowed
  coroutine cancellation.
- Progress values, loading and failure state now belong to the exact region,
  tranche, subproject and year query. Scope changes clear the old result during
  composition rather than waiting for a network response. Successful empty
  results remain valid. Failure shows a localized UA/EN message and a dedicated
  retry which does not reload the payment source, project context or ledger.
- Cancellation is propagated rather than presented as a source failure. These
  optional grouped charts still do not block route readiness or navigation.
  Formulas, source records and database contents are unchanged.
- Three standalone Kotlin/JUnit request-result/cancellation tests and the four
  source-selection tests pass together (7 tests). The 42-pattern web check also
  passes. Localization regression coverage includes the two new message keys.
  The complete Compose build and real-browser validation remain CI requirements;
  they are not certified by these standalone checks.

### Follow-up: stable monthly-chart projections (13 November)

- Import progress updates the screen timer every 400 ms. The screen previously
  rebuilt its scoped chart records on every recomposition, and each monthly
  chart repeated payment grouping, calendar gap filling and tooltip generation.
- The scoped record projection is now remembered by source rows and all four
  scope filters. Works/TS/CSC/equipment aggregates are remembered by records,
  purpose, currency, resolved rates and interface language. Language is included
  explicitly because tooltip captions are localized. Timers and expanded state
  do not affect the amounts. No aggregation formula or network request changes.
- Added regression cases for refreshed amounts, changed conversion rates,
  currency and localized tooltips. These require the frontend test build in CI;
  local Gradle is still blocked before compilation by its loopback error.
  The web regex check passes. No measured wall-clock improvement is asserted.

### Follow-up: avoid eager retired-ledger requests (13 November)

- Financial Monitoring previously requested all legacy financial records before
  loading project context, even when approved payment data was available. Those
  records could also trigger date-specific NBU requests for the hidden ledger.
- Project context now loads independently. Legacy records are requested only
  after an unavailable approved-source attempt and successful context loading;
  pending and loaded-empty sources never trigger this fallback. Route readiness
  does not wait for the optional legacy request. Fallback errors retain a retry.
- Transaction-to-subproject projection is memoized and uses a code index instead
  of repeatedly scanning every project. Code captions use that same project
  context and no longer require legacy records. Monetary values, imports and
  database contents are unchanged.
- Four standalone Kotlin/JUnit source-selection checks pass, including all eight
  fallback-gate combinations. JavaScript syntax and the 42-pattern web regex
  checks pass. A compiled-Compose browser regression now covers pending, empty
  and unavailable sources and counts legacy requests; its execution requires CI
  because local Gradle remains blocked by the loopback connection error.
- No wall-clock loading improvement is claimed without a comparable browser
  measurement. This removes verified redundant work, not every startup cost.

### Follow-up: client payment-source availability (13 November)

- Financial Monitoring monthly charts still selected workbook rows by checking
  whether the mapped transaction list was nonempty. A successfully loaded
  approved register with no dated payments therefore resurrected legacy ledger
  rows, disagreeing with the corrected backend's empty-snapshot policy.
- Selection now distinguishes an available empty register from an unavailable
  source. Pending initial loads do not briefly display unrelated legacy totals;
  an existing snapshot remains authoritative during refresh. The established
  legacy fallback is retained only after a completed unavailable-source attempt.
- Payment monitoring load failures now have a localized UA/EN retry message,
  and coroutine cancellation is propagated. A failed refresh retains the last
  loaded snapshot instead of silently clearing it. Loading does not block the
  surrounding navigation.
- The three payment summary cards show unavailable data as a dash, not as a
  fabricated zero before a response. Genuine zero totals remain zero. The
  programme E4 KPI and existing filter scopes are otherwise unchanged.
- Three focused JVM tests passed for the pure client selection helper; those
  regressions are also included in the browser-test source set. Browser regex
  validation passed all 42 literals. Local Gradle JS/WASM remains blocked by
  the loopback-connection error; CI and a production browser check remain open.

### Follow-up: bilingual region grouping and filtering (13 November)

- Production contains both English Allocation region names and Ukrainian
  Monitoring region names: e.g. Kharkiv and Харківська. Financial progress
  previously removed the word region/область but did not map the two names to
  one identity. This split a region's numerator and denominator into separate
  bars, which then displayed the same localized label. Filtering selected only
  one spelling and could exclude the other source's subprojects.
- Moved the existing UI region dictionary/canonicalizer into shared common
  code. Backend financial grouping and filtering, including financial table PDF
  filtering, now reuse the same identity. UI labels, autocomplete choices and
  Kyiv-city distinction remain intact. Unknown labels are preserved, not
  guessed into an oblast. Canonical lookup and regex patterns are prebuilt.
- Read-only production evidence shows 38 source labels representing 23 regions.
  Running the real shared normalizer against that export preserves all 302
  subprojects. No project records, statuses or monetary values were rewritten;
  the existing active/planned selection policy was not changed.
- Three shared JVM tests passed for all dictionary names, language aliases,
  suffixes, case, whitespace and Kyiv city. Added a backend aggregate regression
  for conservation of both payments and base amounts after alias merging.
  The browser regex validator now scans shared sources too and passed all 42
  literals. Backend CI now runs shared JVM tests and retains their reports as
  well, rather than merely compiling shared code as a server dependency.
  Full local Gradle/WASM remains blocked by the loopback-connection
  error; CI and an authenticated production chart check remain separate checks.

### Follow-up: subproject PDF financial execution (12 November)

- PDF execution summed every payment/advance, including TS, CSC and equipment,
  used only the parent's legacy financial records and divided by the combined
  contract total. Its percentage text was also clamped to 100%, concealing
  genuine above-base expenditure. This differed from Works-only progress charts.
- The PDF route now scopes approved payment transactions to the selected
  subproject (including lot code attribution). It reads parent/part monitoring
  details and applies the same source-Works total, lot sum and legacy fallback
  precedence as Financial Monitoring. A present empty snapshot stays empty.
  Missing snapshots alone permit a lazy, hierarchy-scoped legacy fallback.
- Legacy fallback excludes acts and service/equipment payments, and uses the
  shared decimal display conversion. The financial-progress API now uses that
  same decimal conversion and missing-rate policy, rather than multiplying
  Doubles in a separate branch. No saved records or currencies are changed.
- Only the drawn bar is capped to its available width; the printed percentage
  retains the actual ratio, including values above 100%. Financial-record and
  document listing sections otherwise retain their existing behaviour.
- Five JVM tests passed, including actual generated-PDF text extraction, lot
  attribution, service exclusions, empty-snapshot authority, saved-rate decimal
  conversion and the existing photo/link export tests. A synthetic 125% card
  was rendered and visually inspected: its label and bar fit the section.
  This is local generator verification, not authenticated production-browser
  verification of a newly deployed PDF. Full Gradle CI remains required.

### Follow-up: decimal cent conversion (12 November)

- Reproduced a binary floating-point boundary: `0.84 * 48.125 * 100` is
  4042.4999999999995 and rounds to 4042, although the decimal result is exactly
  4042.5 cents and should round HALF_UP to 4043. The table-export API and PDF
  renderer previously multiplied in a different order and maintained separate
  conversion implementations, permitting a one-kopeck disagreement.
- A shared decimal-cent helper now rounds once using BigDecimal/HALF_UP, the
  rule already used by saved NBU EUR conversions. Financial table sorting and
  PDF cells share the same display helper, saved EUR equivalent and missing-rate
  policy. Existing EUR amounts are not recalculated from a newly fetched rate.
- Dashboard legacy-ledger conversion now uses the database BigDecimal values
  directly instead of passing through Double. Imported monthly payments use
  the same decimal cent rounding. Unknown/nonfinite/overflowing results remain
  unavailable rather than becoming zero or a saturated Long value.
- Three focused JVM tests passed: half-cent cases, negative refund symmetry,
  zero, large exact decimal amounts, saved EUR values and missing/invalid rates.
  No database values, saved exchange rates or source transactions were changed.
  A rendered/browser PDF check and the complete CI build remain separate checks.

### Follow-up: preserve source money precision (12 November)

- Monitoring and Allocation readers previously applied Excel display formatting
  to all registry values. An amount such as 1234.56 formatted as `#,##0` became
  `1,235` in the retained payload. The grouped-number parser cannot recover
  cents already lost at that earlier step, and financial denominators then use
  rounded values rather than the workbook's underlying amounts.
- Dedicated money-field reading now retains the numeric cell value, including
  Excel's cached numeric formula result, as a locale-independent decimal string.
  It does not evaluate the approved workbook's cross-sheet formulas. Textual
  money remains available to the existing source amount parser.
- Monitoring monetary columns and the three Allocation funding columns use
  this path. Dates, percentage display units, identifiers, hyperlinks and other
  fields retain their existing readers. Tests parse complete in-memory XLSX
  fixtures through both real import paths and verify hidden cents, cached
  formula amounts, zero/blank/text values, date and percentage preservation.
- The full-precision registry payload is used on the next approved source
  refresh. Previously rounded payloads are not heuristically reconstructed.
  Legacy project budget/contract columns remain whole-unit Long attributes;
  their schema was not changed by this precision fix. Decimal-aware financial
  calculations can consume the exact retained source values after refresh.

#### Verified precision restoration

- The two approved local source files have exactly the same SHA-256 hashes as
  the workbook copies retained in the original 10 November cutover backup.
  A read-only Apache POI exporter uses their cached numeric values and former
  display values, with the same first/last monitoring row and Allocation
  deduplication rules as the importer. Source XLSX files were not modified.
- The targeted planner matches both workbook provenance and lot code. It only
  restores a monetary key whose retained value equals the former source display
  value, and leaves manually changed or unavailable values alone. Whole-unit
  budget/contract attributes are aligned only if they still match that same
  rounded source amount. No other source fields are reimported.
- Created and restore-verified backup
  `/docker/oms/backups/registry-20261112T095621Z`. Trial database
  `oms_amount_trial_20261112095645` restored precision to 699 monetary keys
  across 160 detail rows. Forty project rows needed whole-unit alignment:
  12 budgets and 31 contract totals (some rows have both).
- The trial compared every non-amount project column, all detail metadata and
  every non-money JSON key. All were unchanged, excluding expected project
  updated_at timestamps. A guarded SQL transaction then applied the same plan
  to production. Its assertion checks both affected-detail and project counts
  before COMMIT; any concurrent edit prevents the entire commit.
- The production recheck found zero eligible precision repairs. All 332 project
  rows, four users, both inspection links and uploaded-file checksums remain
  unchanged. The source payment transactions and currencies were not modified.
  Six Python tests passed for parsing, provenance/matching rules, idempotency,
  preservation of manual amounts and safe SQL literals.

#### Browser CI follow-up

- The c69adc2 form-test run passed Chrome validation and its Chromium date-picker
  checks, but WebKit failed to launch: `libicudata.so.74` was missing. The runner
  installed ICU 78, while the restored browser cache key identified only Linux
  and the Playwright version, not the Ubuntu ABI.
- The browser cache now includes the actual distribution, version and machine
  architecture from `/etc/os-release` and uname. There is no legacy restore-key
  fallback, so a runner-image migration cannot reuse a different Ubuntu build.
  Chromium/WebKit checks remain enabled. A fresh CI run must confirm the fix;
  the prior failed WebKit launch is not represented as a passing Safari test.

### Follow-up: source currency metadata

- Both approved subproject readers populate legacy budget/contract attributes
  from UAH columns. New imported projects nevertheless received currency EUR,
  and updates did not synchronize that label. The individual PDF uses this
  metadata, so it could print a hryvnia amount with an EUR suffix.
- Import creation and both update branches now explicitly assign source currency
  UAH. This does not change the default or currency choice in manual project
  forms and does not convert or multiply any monetary values.
- A read-only comparison found 13 approved-source rows with the incorrect EUR
  label and an exact matching source budget. The currency-only repair mode
  excludes manual/nonmatching budgets and other currencies, guards the old
  metadata and source values, and reuses the trialled transactional repair.
- Backup `/docker/oms/backups/registry-20261110T145538Z` passed restore validation.
  Trial schema `oms_amount_trial_20261110145602` changed 13 currency labels,
  zero budgets and zero contract amounts. Comparing every other project column
  found no changes beyond automatic updated_at timestamps.
- The same guarded plan corrected production. A subsequent plan had zero rows;
  all 332 project identities, four users, both inspection links and uploaded-file
  checksums were preserved. Existing downloaded PDF files are not rewritten;
  a newly requested PDF reads the corrected project currency.
- Three Kotlin tests exercise the actual private registry row parser and currency
  policy; four Python tests cover repair scope, parsing and SQL literals. Full
  Gradle/interactive PDF verification remains separate from these focused checks.

### Follow-up: grouped money values during subproject import

- The monitoring row reader replaced every comma with a decimal point.
  Excel-formatted `1,234,567.89` consequently became `1.234.567.89`, failed
  numeric parsing and was silently replaced by zero. This path writes the
  legacy project budget and signed-contract attributes; the original registry
  JSON and source workbook remain intact.
- Registry rows now reuse `monitoringSourceAmount`, already used for dashboard
  funding and financial bases, instead of maintaining a conflicting parser.
  Tests exercise the actual Row.number entry point with Apache POI-generated
  grouped display text, grouped integers, decimal commas, NBSP grouping and
  invalid/blank cells. Missing amounts retain the existing zero fallback.
- This code correction applies on the next approved registry refresh. It does
  not silently rewrite existing production project attributes; refreshed
  source amounts must be reconciled before a separate data correction.

#### Verified production amount repair

- Read-only diagnosis confirmed the defect in existing attributes: 193 of 196
  active subprojects and all 29 parts had zero budgets, despite nonzero retained
  source amounts. A separate case stored `888,956` as 888 rather than 888956.
- Created and restore-verified backup
  `/docker/oms/backups/registry-20261110T143436Z` (database and uploaded files).
  A guarded repair was first applied to the isolated restored schema
  `oms_amount_trial_20261110143457`: 225 budgets and 67 contract totals changed.
  Comparison of every other project column found no changes except MySQL's
  automatic `updated_at` timestamps.
- Applied the same plan to production in one transaction. Each row required
  its stored old values and source payload to match the read-only plan; an
  affected-row assertion aborts the batch before COMMIT on any mismatch.
  Values not matching the proven legacy-parser result are deliberately skipped
  during planning rather than overwriting potential manual corrections.
- Post-repair planning found zero remaining eligible corrections. All 196
  active subprojects and 29 parts now have nonzero source budgets. All 332
  hierarchy rows, four users, both inspection identities/project links and
  every uploaded-file checksum were preserved. Null contracts with no positive
  source contract remain null; they are not fabricated.
- No application restart or wider import was needed: the project read cache
  expires after 30 seconds. This verifies database values and retained links,
  not a completed interactive browser review of every financial chart.

### Follow-up: lot contract bases in financial execution

- Financial progress fetched monitoring details for parent subprojects only.
  Where DB Table publishes separate lot rows rather than a parent total,
  that discarded the actual Works contract bases and used a synthetic parent
  fallback (which can represent just its first lot).
- The request now fetches details for parts of the selected subprojects as well.
  A positive source parent Works total takes precedence; otherwise positive
  source lot Works amounts are summed once. Missing lot amounts are not invented.
  The established stored-contract/approved-budget fallback remains when neither
  source level has an available positive base. No source records are changed.
- DB Table rows 57–62 (KH08_07) supply four Works amounts across six lots:
  2,867,194.45 + 2,896,685.69 + 2,929,089.77 + 2,905,436.22 =
  UAH 11,598,406.13. Rows 67–68 (KH08_09) likewise publish lot amounts,
  not a separate parent total. Regression tests cover lot aggregation,
  parent-total precedence, missing values and exclusion of nonpositive values.

### Follow-up: financial-progress source selection

- Financial progress by region, sector and construction type tested whether
  imported transactions were nonempty before choosing the approved source.
  An existing snapshot with no dated payments therefore revived legacy ledger
  entries. The calculation now distinguishes an absent snapshot from an empty
  imported register, matching the monthly dashboard source policy.
- The source selection and Works-payment grouping are tested together: TS/CSC,
  payments outside the selected year and subprojects outside the selection are
  excluded; lot payments retain their parent attribution. Only an absent
  snapshot invokes the legacy reader, which remains lazy.
- The real-source standalone check reconciles the construction numerator to
  UAH 307,988,578.28 and verifies empty-register, empty-year and empty-scope cases.
  This correction does not change the current nonempty imported source totals.

### Follow-up: procurement counting grain

- The signed **construction** contract count chart on Procurement also counted
  TS and CSC rows. It now includes only Works; clicking its month applies the
  same Works restriction to the table. The monetary chart still covers all
  contract types, as its title specifies, and clears that restriction on click.
- The approved 10 November file contains 139 contract rows. After applying the
  tracker's latest-attempt status (the existing import policy), 129 rows belong
  to signed procedures, representing 124 distinct Procurement IDs. Their raw
  contract amounts total UAH 2,858,853,119.11; the tracker totals, counted once
  per procedure, total UAH 2,856,947,600.40. These are different source measures,
  not interchangeable inputs to one formula. The construction count is 61,
  rather than the former 129 mixed-type rows, and every Works row has a date.
- Five TS procedures have two contract rows. For example, KV08_01_TS$1 is
  terminated in DB6.3 and KV08_01_TS$2 is signed. Both rows are marked Active;
  the procedure tracker reports the latest attempt. Do not drop history or
  silently replace procedure statuses with contract statuses to force agreement.
  Distinguishing these two statuses in OMS remains a separate modelling issue.
- A read-only standalone reconciliation exercises the actual XLSX importer and
  checks row counts and both independently scoped sums against this snapshot.

### Follow-up: monthly disbursement aggregation

- Ledger fallback uses payment date when available, then record date. A payment
  without a saved EUR equivalent is retained in the known UAH total.
- A persisted approved snapshot is authoritative even for an empty tranche;
  an empty result no longer revives unrelated legacy financial records.
- Independent availability is retained for each currency. An additive, defaulted
  API flag marks incomplete EUR totals without changing the numeric field's
  wire type. The updated chart does not present an unknown conversion as zero
  and shows the existing missing-rate warning.
- Months between the first and last payment are continuous and genuinely empty
  months are zero. No months are inserted before the first or after the last.
- Standalone tests verify mixed currency availability, tranche aliases and
  calendar gaps. An additional real-source reconciliation verifies all 93 rows,
  UAH 309,119,376.61, the empty tranche-B result and the January 2026 zero.

### Follow-up: funding baseline and physical lot weighting

- Physical-completion weighting now uses the procurement lot identifier when
  present. The parent subproject code remains the final aggregation key, but
  cannot be used to look up a particular lot's construction contract.
- Approved funding prefers the AT investment value, then monitoring/allocation
  `eibCost`, then the budget fallback. A signed construction contract is not
  an approved-financing fallback. A production query found 136 active
  subprojects without an AT amount persisted in procurement but with an EIB
  amount in their monitoring snapshot.
- The legacy dashboard funding list is scoped to active subprojects, matching
  the overview instead of including planned screening candidates.
- Source amount parsing accepts Excel thousands grouping (`6,484,316`), mixed
  US decimal formatting (`1,234.56`) and Ukrainian decimal formatting
  (`1 234,56`). Invalid and nonfinite values remain unavailable, not zero.
- Read-only production inspection found no simultaneous nonblank parent and
  lot progress rows for the same subproject. No speculative deduplication of
  such observations was introduced.

### Follow-up: lot attribution and percentage units

- `paymentMonitoringSubprojectCode` removed `#Lot` before stripping `_W`/`_TS`/
  `_CSC`. For `KH08_07#Lot1_W`, that produced `KH08` and lost the project match.
  The suffix order is corrected and plain subproject codes are preserved.
  Independent source inspection found 22 dated Works lot payments totalling
  UAH 49,522,160.76 affected by this attribution defect in grouped progress.
  The payments themselves were stored; this is not missing imported money.
- Numeric Excel cells with a percent number format are fractions. The XLSX
  reader now converts only such cells to API percentage points before storage:
  `0.765 -> 76.5`. Plain numeric `76.5` and textual `76.5%` remain 76.5.
  This also preserves a genuine `0.5%` cell instead of applying a value-based
  heuristic that confuses small percentages with fractions.
- Existing stored share values require a payment workbook refresh after the
  corrected backend is deployed. No heuristic rewrite of historical database
  values is performed. Lot attribution takes effect immediately on new reads
  after deployment and requires no reimport.

### Follow-up: source formula semantics and deployed refresh

- Verified the source formulas directly: planned share I5 is `C5/B5`; factual
  share J5 is `F5/B5`. J therefore describes funding execution (including payments
  in progress), not an EIB contribution percentage applicable to paid amount G.
- Removed the erroneous `G * J / 100` KPI calculation. Sum primary G values
  within the selected active-subproject scope instead; W/TS/CSC components remain
  excluded from the parent total. A missing J no longer discards a valid G.
- Backend, frontend and deployment CI passed for `b323964`. The corrected reader
  was exercised against a restored trial database and then used for a payment-only
  production refresh with a fresh verified backup. VN08_01 now stores planned
  share 76.78 and factual share 10.74 percentage points. All 93 transactions and
  their total remain unchanged; users and inspection links passed comparison.
- KPI helper tests explicitly cover a low factual execution ratio, a missing
  ratio, scope exclusion and exclusion of component rows.

Standalone Kotlin checks execute the shared canonicalization and verify alias
grouping conserves totals. Backend regression tests cover the weighted ratio,
aliases and construction-only denominator fallback, including fractional UAH.
Local Gradle tests/build remain blocked by `Unable to establish loopback
connection`; CI is required for the complete backend/frontend build.

This is a review of the affected financial aggregations and source controls,
not a certification that every Excel formula or every other OMS calculation
has been audited. Further source-formula and module-wide review remains open.
