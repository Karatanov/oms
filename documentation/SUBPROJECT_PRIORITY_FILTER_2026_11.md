# Subproject priority direction filter — 14 November 2026

- The screen retained `sectorFilter` state but rendered no control over the
  Priority column. Its predicate compared the legacy `project.sector`, while
  the displayed field used `registry.priority`, so merely exposing that control
  would still have filtered a different attribute.
- The Priority column now uses the shared overlay picker, populated from actual
  project/part priorities and excluding the programme root. Canonical keys merge
  UA/EN aliases; labels and ordering follow the active language. Individual All
  and global Reset actions reuse the existing table behavior. Pagination observes
  the changed filtered list as before. Other filters and Active default remain.
- Cells, sorting, filter options and PDF filter descriptions use one priority
  label path. A missing registry priority falls back to the project sector.
  Single-language English inputs are translated, not handled only by splitting
  a slash. Sorting invalidates on language changes, without clearing selection.
- Priority directions preserve the two source shelter subtypes instead of
  collapsing them into the chart-level Shelter sector. A read-only production
  audit of 302 subprojects found 17 raw captions representing nine directions;
  regression coverage checks this complete caption set. No database changes.
- Shared classification and existing sector regressions pass locally via the
  standalone Kotlin/JUnit compiler. Web tests cover source-vs-legacy selection,
  clearing the filter and UA/EN labels, including distinct shelters. Those web
  tests require CI: local Gradle still fails before compilation with
  `Unable to establish loopback connection`. Full Compose build and visual
  browser verification are not established by the standalone checks.
