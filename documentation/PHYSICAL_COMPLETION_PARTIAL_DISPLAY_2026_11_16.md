# Keep valid physical-completion bars visible — 16 November 2026

The user requested omission of only invalid subprojects rather than hiding the entire physical-completion chart. This supersedes the all-or-nothing presentation in PHYSICAL_COMPLETION_SOURCE_GUARD_2026_11_15.md; the import guard and source data remain unchanged.

`physicalCompletionSeries` partitions the selected population into valid and invalid projects. Genuine zero values remain valid; flagged compatibility placeholders and non-finite values are omitted. Other values, ordering and existing financial anomalies above 100% are not altered. Changing the oblast filter recalculates both the chart and exclusion notice.

The UA/EN note includes the number and codes of omitted subprojects. It is visible alongside the chart and included in the PNG export subtitle, so an exported partial selection does not silently imply complete coverage. If every selected project is invalid, the chart has no bars and retains the explanatory note/empty state.

Read-only production API verification found one flagged project: MY08_02, the boiler-room reconstruction at Voznesensky Lyceum No. 10 in Mykolaiv oblast. A matching read-only database query confirmed `completed_work_pct` and `registry_json.completedWorkPct` both contain literal `697 192,92`. This is not a valid physical percentage; no replacement percentage was invented and no database record was updated.

Regression coverage includes mixed valid/invalid selections, genuine zero, all-invalid selections, source refresh recovery, non-finite values and backwards-compatible DTO parsing. Full frontend build/deployment/browser status is tracked separately from the focused tests.
