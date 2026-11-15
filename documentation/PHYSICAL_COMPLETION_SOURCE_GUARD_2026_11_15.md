# Physical completion source guard — 15 November 2026

## Production/source investigation

Release `bcdf46a` passed all four workflows and was verified by exact production revision `bcdf46af8675`, API health and readiness. This is release/API evidence, not browser UI verification.

A fresh read-only API/database audit passed 596 consistency checks but still found MY08_02's physical percentage `697192.92%`. Consistency with stored data is not proof that the source input is valid. The old audit's duration-comparison section is historical hardcoded characterization, not verification of the current PDF exporters.

The provided local `DB_Monitoring table.xlsx` (SHA-256 `523cafdd8fddf05089a2bffbba3ac2aef8c80dedf6308ad847f91e69e8b0ad91`) has truly blank CC106 and CD106, and cached numeric zero in CE106. The current approved Google workbook export (SHA-256 of the downloaded bytes `8ed704abdbec2b81093b9fc4c5c4aead0176ab409c63f8f1cafbebe19d9ac5a5`) is different: CB106 has a newer 31 October certificate date, CC106 contains literal text `697 192,92` with General format under **Share of completed works, %**, CD106 contains `0.0971` and CE106 `0.0999`, both percentage-formatted.

The server logged automatic monitoring refresh start at `2026-11-15 17:21:29.628`, following the configured ten-minute startup delay. A read-only environment check confirmed the default approved Google source and enabled automatic refresh, without disclosing other environment values. The database now contains the online source's CC/CD/CE values. Thus repeating the earlier local-file repair would not resolve the source disagreement and could erase newer source data. No repeat repair was performed.

## Minimal safeguards

- One physical completion parser preserves blank versus zero, supports fraction/text/percentage formats, and accepts only 0–100% physical completion.
- The DB Table importer validates every supplied physical-completion cell during parsing, before the registry replacement transaction. An invalid value fails with its subproject, cell and expected range, preserving the existing snapshot. No division, clipping or guessed replacement is used to turn a currency-like input into a percentage.
- For invalid values already stored, both dashboard endpoints propagate `hasCompleteAmounts=false`. The numeric field remains for wire compatibility; its placeholder is not a real zero and is never plotted by the updated frontend.
- The physical completion chart exposes a UA/EN explanation rather than plotting an invalid or incomplete selection. The existing oblast selector remains available, so a selection containing only complete projects still renders normally. PNG export is unavailable for the hidden series.
- Legitimate financial/aggregate anomalies above 100% are not clamped. This guard applies to the physical source field, not cash overspend or EIB payment ratios.

No database values, source workbook cells, schema, user permissions, financial aggregation or imported payment values were changed.

## Required source action

The owner of the approved Google monitoring workbook must correct **DB Table!CC106 (MY08_02)** to the actual physical completion percentage or a deliberate blank. `697 192,92` is not a valid percentage. After correction, refresh the subproject register. Do not replace the newer online CD/CE values with older local blanks/zero without source-owner reconciliation.

## Verification boundaries

77 focused calculation/serialization tests passed, covering physical bounds, missing versus zero, rejected monetary text, flag compatibility, region-selected availability and unchanged over-100% aggregate values. Fourteen checks of the actual Apache POI importer and physical parser passed (including overlapping pure-parser coverage), using its real private parse function. All production server sources compiled with the standalone cached compiler; pre-existing warnings remain.

Without invoking any database operation, the real parser accepted 145 rows from the provided local file and rejected the downloaded online file with `Invalid physical work completion for MY08_02 at CC106: '697 192,92'`. Web regex/cursor policies and whitespace checks also passed. Complete Gradle/frontend and production browser results must be reported separately; the Windows Gradle loopback limitation remains.
