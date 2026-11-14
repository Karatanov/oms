# Subproject registry reference links

## Verified source distinction

Read-only production audit of DREAM, design, estimate and expertise references:

| Reference | HTTP(S) URLs | Document numbers without URLs |
| --- | ---: | ---: |
| DREAM | 132 | 0 |
| Design documentation | 95 | 3 |
| Estimate documentation | 32 | 17 |
| Expertise | 92 | 6 |

The original `101112026/DB_Monitoring table.xlsx`, `DB Table`, BJ:BL contains
no hyperlink targets for those 26 registration numbers. They are not lost
Excel hyperlinks. One design reference is the placeholder `0`.

## Presentation fix

- Preserve valid HTTP(S) URLs and trim surrounding whitespace.
- Accept scheme-less links only for the known DREAM/e-construction hosts;
  accept protocol-relative HTTP links as HTTPS.
- Recognized e-construction registration numbers open the official
  `/search_in_registers?search=` route with the exact URL-encoded source number.
  A search link does not imply that the provider can find that document.
- Underline clickable references and distinguish document/search in localized
  tooltips. Open safely in a separate tab without an opener.
- Do not turn placeholders, arbitrary text, unsafe schemes or credential-bearing
  URLs into links. Preserve source data; no database/import changes.

## Verification

- Actual link helper compiled with standalone Kotlin/JVM: four JUnit tests pass.
- Browser-compatible regex check: 44 expressions pass.
- Button cursor policy and whitespace checks pass.
- Compose JS compile attempted; Gradle fails before compilation with
  `java.io.IOException: Unable to establish loopback connection`.
- New production UI/browser behavior has not been verified locally.

`deploy/vps/audit-subproject-links.sh` provides the read-only source audit.
