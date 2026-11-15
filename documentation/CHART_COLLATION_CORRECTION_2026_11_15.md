# Chart label collation correction

## Confirmed cause

The common chart sorter compared lowercased strings by Unicode code point. Ukrainian І/Ї/Є/Ґ therefore did not follow the Ukrainian alphabet (for example, Івано-Франківська sorted after Харківська). Financial progress cards additionally sorted untranslated source keys before rendering their translated labels.

## Correction

- Chart labels use a cached native `Intl.Collator` for the active interface locale (`uk`/`en`), with case-insensitive, accent-sensitive comparison and lexical label numbers (`numeric: false`). Switching language selects the appropriate collator.
- Both bar sorting and metric-list sorting retain the existing primary direction and ascending alphabetical tie-break for equal numeric values. Source rows and chart payloads are not mutated.
- Financial progress cards sort the exact localized text they display, reusing one label function for rendering and comparison. Numeric values, filters, chart formulas, database records and chronological month ordering are unchanged.
- The browser adapter is compiled inside the bundle, not installed as an extra global HTML function or downloaded dependency. It uses the documented package-level `js()`/external-interface surface for JS/WASM, avoiding `@JsFun`: https://kotlinlang.org/docs/wasm-js-interop.html

## Verification

- The exact production native factory passed 3 Node tests and a headless installed Chrome check: Ukrainian alphabet, English accents/case and locale switching/mixed-script ordering.
- The new adapter compiled independently to both JS and WASM klibs with the cached Kotlin 2.3.0 compiler. This is not a full application/WASM runtime check.
- The existing focused JVM suite still passes 41 tests. A separate sorter audit confirms numeric directions, ties, source immutability and the reproduced Ukrainian order. That JVM-only audit adapts the native boundary to Java Collator; it is not presented as browser verification.
- Six Kotlin browser regressions cover the actual sorter/adapter and displayed-label precedence. CI also runs the native factory in Chromium, WebKit and Firefox; those engine results and the full frontend build must be reported separately after execution.

No new runtime dependency, backend API or database migration is introduced.
