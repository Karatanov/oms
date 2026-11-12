# Financial aggregation review, 10 November 2026

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
