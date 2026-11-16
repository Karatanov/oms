# Payment source numeric parsing — 2026-11-16

## Reproduction

Three new regressions failed on the actual CSV importer before correction:

- monetary `1,234` became 1.234 instead of 1234;
- `1\u202f234,56` (narrow nonbreaking grouping space) became null;
- `NaN` survived as a numeric indicator instead of being rejected.

The same helper reads text-valued workbook cells, the bundled CSV source and
fallback text dashboard controls. This is a demonstrated parser defect, not a
claim that the current production workbook has these erroneous text forms.

## Correction and preservation rules

The parser now receives monetary versus percentage context from the actual
column heading. Raw typed Excel values and the existing numeric percentage-unit
conversion are retained; the new grouping rule applies to text numbers.

| Input / context | Result |
| --- | ---: |
| Money `1,234` | 1234 |
| Money `1,234,567` | 1234567 |
| Money `1,234,567.89` | 1234567.89 |
| Money `1 234,56`, NBSP or narrow NBSP equivalent | 1234.56 |
| Money `-1,234` | -1234 |
| Money `0,995` | 0.995 |
| Percentage `1,234%` | 1.234 percentage points |

Comma-only monetary grouping is recognized only as complete groups of three
after a nonzero leading group. A leading-zero comma fraction is preserved.
Percentage text retains the existing decimal-comma interpretation; monetary
grouping must not multiply a localized percentage by 1000. Existing US mixed
comma/dot interpretation is not redesigned in this pass.

Non-finite text values (NaN, infinities, exponent overflow) raise a validation
error during parsing, before repository replacement. Raw numeric/cached numeric
workbook values also receive a finite-value check. These values are not
converted to zero, persisted as invalid money or silently dropped as an unknown
equivalent. Existing blank, dash and unrecognized/malformed-text handling stays
nullable; this does not claim that every possible malformed number now fails
the whole import. Negative payments/remaining values and real zero are retained.

No database mutation, workbook edit, source refresh or FX lookup was performed.
There is no reinterpretation of already-saved amounts without reimporting an
approved source. The current deployment was independently verified as
`5bb683875316`; its frontend build, image publish and VPS deployment jobs passed.
Its checkout phase took time but no checkout failure was verified, so the
deployment workflow was not speculatively changed.

## Verification

Five new regressions cover grouping, localized spaces, negative/zero/fractional
amounts, percentage context, non-finite rejection and a real in-memory XLSX
roundtrip of text-valued money and percentage cells. All 144 focused Kotlin
tests, 1,000 existing decimal fixtures and 23 Python regressions pass.

Full server source compilation passed using the direct Kotlin 2.3 compiler,
which bypasses the local Gradle loopback limitation. Two additional server DTO
serialization tests passed (146 distinct Kotlin tests including the focused
runner). Existing compiler and standalone logging-provider warnings remain.
This is not claimed as a local Gradle build. Workbook fixtures are internal
software tests, not customer file edits.
No live import or real-browser verification of this new parser is claimed.
