# Decimal dashboard totals — 2026-11-16

## Verified baseline

Production `/version` is `c23b3a5e9eaf`; all four workflows for that release
completed successfully. A fresh independent read-only SQL/API audit passed all
49 conservation checks: monthly cash totals, primary monitoring payments, signed
contract identities/value and tranche partitions.

The API nevertheless exposes binary accumulation tails:

| Field | Observed JSON value | Decimal sum of the same source values |
| --- | ---: | ---: |
| Signed contracts, UAH | 2856947600.3999996 | 2856947600.40 |
| Approved funding, EUR | 139598968.64000008 | 139598968.64 |
| Primary monitoring paid, UAH | 304888750.25999993 | 304888750.26 |
| Approved funding A, UAH (preceding scope audit) | 4253692449.090001 | 4253692449.09 |

These are sub-cent accumulation artifacts, not evidence of missing payments or
permission to replace source values. The existing cent-tolerance checks passed;
they did not assert canonical decimal accumulation.

## Correction

`DashboardAmountTotal.kt` sums BigDecimal inputs and converts only the final
result to the existing Double wire type. It does not round or truncate individual
records, change currencies, deduplicate extra records or change source authority.
Non-finite totals fail rather than turning into a partial finite subtotal.

Applied to:

- national approved funding in UAH/EUR;
- national signed-contract value and contract-type breakdowns, retaining database
  decimals until aggregation;
- signed Works contract bases by exact lot and parent aggregation;
- primary monitoring paid amounts, preserving the existing component exclusion,
  subproject selection and null/non-finite input handling.

The change does not make Double an arbitrary-precision wire format. It removes
repeated binary addition from these paths without a breaking DTO change. It does
not claim to repair unknown currencies, missing conversions or source precision
already lost before these helpers receive an input.

## Checks and boundaries

110 focused Kotlin tests passed, including four new decimal accumulator cases
and a new actual primary-monitoring aggregation regression. Existing 1,000
decimal fixtures, monthly completeness, W/TS/CSC separation, sorting and physical
completion checks passed. Source precision below one cent and negative
adjustments are retained by the accumulator, not rounded away.

No production DB or XLSX edits were made. The read-only audit still finds five
small arithmetic differences in the stored payment source and two cumulative
versus dated-payment differences. These are not automatically overwritten:
source totals can represent different rounding or reporting cutoffs. In
particular, KH08_09 cumulative versus dated differs by 7,254,317.30 UAH and
CH08_03 has dated payments but no primary cumulative paid value. The current
iteration does not substitute one measure for the other.

Full server source compilation passed with the direct Kotlin 2.3 compiler and
existing dependencies; unrelated pre-existing warnings remain. This bypasses the
local Gradle loopback limitation and is not claimed as a Gradle build. The 16
offline budget/planner Python tests also passed. Commit, CI and deployment status
are reported separately.
The wider financial/formula audit remains ongoing; no real-browser verification
is claimed for this server-only numerical correction.
