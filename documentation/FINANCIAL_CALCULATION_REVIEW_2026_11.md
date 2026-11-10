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
