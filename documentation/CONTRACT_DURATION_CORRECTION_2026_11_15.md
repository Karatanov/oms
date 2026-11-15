# Unified project duration display

## Cause

The form/detail UI rounded days / 30.4375, the individual PDF rounded days / 30.44, while the table PDF and API registry fallback rounded days / 30.44 **up** and forced at least one month. This produced different values for the same period (518 days: 17/18; 624 days: 21/20/21). The details client DTO also ignored the backend's registry payload, so it could not display the explicitly imported duration that the subprojects table already used.

## Correction

One shared commonMain function now owns presentation of project duration:

1. A valid explicit duration in the approved registry takes precedence, including zero and decimal months.
2. Otherwise use the existing UI convention: nearest whole month using a 365.25-day year (365.25 / 12 days), calculated as the exact rational 487 / 16 with overflow-safe integer arithmetic.
3. Missing or negative periods are unavailable, not an invented month. A zero-day interval remains zero.

The form's date-derived preview, project details, project table duration sorting/display, API registry fallback and both project PDF exports use this function. Technical supervision/consultant durations use the same fallback. Existing UA/EN unit labels are preserved. Database dates/source durations and the separate procurement actualised-calendar-month calculation are not changed.

## Regression coverage

- Shared tests cover the two observed discrepancies, source precedence, decimal source values, zero/short/negative/missing periods, invalid source values and Long.MAX_VALUE.
- A browser regression runs representative cases with JS Long arithmetic.
- A PDF regression compares API duration, individual card and landscape table in both languages for 518/624 days and an explicit imported six-month duration.
- The focused standalone Kotlin runner includes the shared regressions. Full Gradle/CI build and production deployment are tracked separately.

Local results: 20 focused JUnit tests and all 6 `ProjectPdfExportServiceTest` tests passed. Server production sources compiled directly with the cached Kotlin compiler/dependencies; this bypasses the Windows Gradle loopback limitation and does not claim Gradle packaging or a browser production test. No production database changes are needed for this correction.
