# Decimal currency rounding correction

## Verified cause

The browser multiplied/divided binary Doubles before `roundToLong()`, while the server's monetary helpers used decimal `BigDecimal` with HALF_UP. For example, 0.29 EUR at 48.5 UAH/EUR should yield 1,407 kopecks, but the browser could yield 1,406. The existing 1,000-input characterization reproduced 35 one-kopeck discrepancies. Negative half ties and overflowing amounts also had different behavior (saturation vs unavailable).

## Correction

`shared/commonMain/DecimalMoney.kt` owns decimal multiplication/division and a single HALF_UP rounding step to minor units for JVM/JS/WASM. It interprets decimal input representations, supports scientific notation, uses bounded decimal digit arithmetic without new dependencies, and returns null for invalid rates/nonfinite values/Long overflow. Negative half ties round away from zero, matching BigDecimal HALF_UP.

Both the frontend `displayAmountCents` and the server Double overload of `moneyCents` use this shared implementation. The server BigDecimal overload remains available for exact stored decimal amounts and serves as an independent reference. Saved EUR minor units (including a genuine zero), frozen-rate precedence, historical-rate fallback and the legacy EUR rate=1 sentinel are unchanged. No payment/source amount, rate, database row or schema is modified.

## Checks

- 31 focused JUnit tests passed, including the new shared/client money regressions and existing financial helpers.
- The JVM parity test makes 24,000 comparisons against independent BigDecimal multiplication/division across positive/negative cent inputs and six rates.
- The former characterization audit now requires zero frontend discrepancies for the 1,000-input set and checks saved values and unknown-rate behavior.
- Shared and browser regressions cover the actual conversion function, half-cent ties, negatives, scientific notation, tiny values, zero, missing/sentinel rates and Long bounds/overflow.
- Regex and button-cursor checks pass. Full CI, browser execution and deployment are separate checks, not inferred from the focused JVM runner.
