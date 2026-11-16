# Budget representations and UAH registry fallbacks — 16 November 2026

## Independent evidence, not API self-comparison

The project registry API fills absent source fields from saved `project_amounts` or legacy whole-UAH fields. Consequently, equality between an API registry field and a saved amount is not necessarily independent source corroboration. The new read-only audit joins API projections to raw stored monitoring JSON exported from a read-only transaction; it never uses the API fallback as source evidence.

At production revision `3861be3b7b5f` (all workflows, health/readiness and exact revision verified), the 331 subproject/part rows classify as:

- 129 consistent within the legacy whole-hryvnia precision.
- 48 with a saved budget snapshot different from raw stored source funding, while the legacy value matches the source within its precision.
- 3 with a differing legacy budget while the saved snapshot matches raw source funding: KH08_02, CV08_04 and KH08_07.
- 45 without a saved budget snapshot; this alone is not an error because source/legacy representations remain available.
- 106 without raw stored source funding; API fallback values must not be counted as source evidence.

All 51 conflicting saved snapshots are UAH and have `conversionEdited=false`; no manual converted override was found among them. This classification is evidence for a separate guarded reconciliation, **not** an executed monetary repair. No database records were changed.

## Confirmed mapper currency defect fixed

`Project.registryValues` used the original `ProjectAmount.amount` for fallback columns whose headings are denominated in UAH. For example, a 100.01 EUR budget with frozen UAH equivalent 5000.50 could appear as `100.01` in `fundingTotal`, falsely implying hryvnias.

The mapper now uses `ProjectAmount.uah`: original UAH where appropriate, otherwise the already saved UAH conversion. Explicit monitoring-source fields keep priority. Original API amount/currency/snapshot metadata are unchanged; no fresh FX lookup, conversion override, persistence change or rounding policy is introduced. Genuine zero and manual saved equivalent are preserved. This corrects the fallback for all monetary registry fields using the helper, not only total budget.

## Verification and open work

All production server sources and the new DTO tests compiled successfully with the cached standalone compiler; pre-existing warnings remain. Four actual mapper regressions passed (10 monetary fallback keys, EUR/UAH, cent precision, explicit source/zero precedence, saved manual equivalence and model immutability). Five offline audit-classification tests passed and were added to backend CI. Regex/cursor/whitespace checks passed.

The 51 monetary disagreements remain unrepaired pending a separately guarded, backup/trial-verified reconciliation. The read-only audit does not change import behavior or source priorities. Full CI/deployment/live verification is recorded separately; a successful standalone compile is not a complete Gradle or browser check.
