# Source number conventions — 2026-11-16

Follow-up to the payment-number parser release `f68d27e`.

## Reproduced defects

Three new regressions failed against the existing payment parser:

- `1.234,56` became 1.23456 rather than 1234.56;
- `+1,234` became 1.234 rather than 1234;
- malformed `12,34.56` was silently accepted as 1234.56.

The same US-only mixed-separator simplification existed in monitoring budget
parsing, dashboard source amounts and the independent budget audit/repair-plan
input parser. Fixing only payments would leave inconsistent interpretations
between runtime paths and potentially unsafe diagnostic repair inputs.

## Shared runtime interpretation

`SourceNumericText.kt` now normalizes supported formats once for payments,
monitoring dashboard amounts and saved budget conversion updates:

| Supported text | Normalized amount |
| --- | ---: |
| `1,234.56`, `1.234,56`, signed variants | 1234.56 |
| `1,234,567.89`, `1.234.567,89` | 1234567.89 |
| `1.234.567` (multiple complete groups) | 1234567 |
| `1.234` (single dot) | 1.234 |
| `0,995` (leading-zero fraction) | 0.995 |
| Percentage `1,234%` | 1.234 percentage points |

Grouping must be structurally valid. An additional decimal separator, incomplete
groups or mixed separators that match neither supported convention are not
guessed. Scientific exponents remain supported with valid mixed grouping.
ASCII/NBSP/narrow-NBSP grouping spaces are removed. Sign/range validation and
percentage-suffix handling remain in the owning callers; monitoring does not
start accepting percent suffixes as currency.

Payment parsing rejects malformed mixed separators before replacement. The
monitoring importer explicitly rejects malformed mixed funding/contract totals
at its typed-number boundary, so the usual missing-value fallback cannot turn
that rejected format into a zero budget. Such an exception rolls back the import
transaction. Other dashboard readers may retain null/fallback behavior for old
unavailable values; this change does not claim that every invalid source text
now rejects a complete monitoring workbook.

`tools/audit_budget_representations.py` implements the same supported monetary
conventions independently with Decimal. Unsupported mixed strings return None,
which excludes them from automated repair planning instead of guessing a value.
No live budget repair is performed by these code changes.

## Verification and boundaries

- Eight payment-number regressions pass, including the three initially failing
  cases and existing percentage, non-finite, sign, zero and XLSX text-cell tests.
- Monitoring dashboard/BigDecimal budget parity checks pass for US, European,
  signed and spaced values, malformed separators and negative-budget protection.
- The focused runner covers the shared normalizer and import conversion helper,
  alongside the existing 1,000 decimal fixtures; all 149 focused Kotlin tests pass.
- All 24 Python regressions pass, including new audit-parser guards.
- The import conversion helper used by the monitoring Row has a regression
  proving invalid mixed funding throws rather than returning zero. This does
  not claim an end-to-end live workbook replacement test; full-server results
  are reported separately.

The independent live budget audit still reports 180 consistent saved snapshots,
45 records without snapshots and 106 without a raw source budget, with no saved
snapshot conflicts. This was a read-only check, not an automatic plan application.

Full server source compilation passed with the direct Kotlin 2.3 compiler,
bypassing the local Gradle loopback limitation. Two additional server DTO tests
passed, for 151 distinct Kotlin tests including the focused runner. Existing
compiler and standalone test logging-provider warnings remain. This is not
claimed as a local Gradle/Compose build; release status is tracked separately.

No production workbook, database value, source download setting, FX conversion
rate, permission or frontend layout changed. No approved-source reimport or
browser interaction was performed. Current native decimal values are not
rewritten merely because more text conventions are now understood. Release
`f68d27e6e599` was independently confirmed deployed with all four workflows
successful before these changes; the new release status is separate.
