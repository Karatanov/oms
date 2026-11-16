"""Offline coverage regressions; never load or author customer spreadsheets."""
import unittest
from audit_payment_rollup_cutoffs import annual_sum_columns, omitted_payment_months, source_date


class PaymentRollupCutoffsTest(unittest.TestCase):
    def test_supported_sum_and_row_guards(self):
        self.assertEqual(annual_sum_columns('=LET(total, SUM(J318:R318), IF(total=0, "", total))', 318), set(range(10, 19)))
        for formula in ('=SUM(J318:R319)', '=SUM(J318:R318)+SUM(S318:T318)', '=SUM(J318:R318)+S318',
                        '=SUMIFS(A1:A3,B1:B3,C1)', '=SUM(R318:J318)'):
            self.assertIsNone(annual_sum_columns(formula, 318))
        self.assertIsNone(annual_sum_columns(100, 318))

    def test_actual_november_payment_is_not_covered_by_october_cutoff(self):
        calendar = {10: (2026, 2), 18: (2026, 10), 19: (2026, 11)}
        rows = [dict(date='2026-11-03', amount='7254317.30', reference='November!E3'),
                dict(date='2026-10-19', amount='10', reference='October!E9')]
        omitted = omitted_payment_months(2026, set(range(10, 19)), calendar, rows)
        self.assertEqual(omitted, [dict(month='2026-11', datedAmountUah='7254317.30', sourceRows=['November!E3'])])

    def test_pending_dates_and_other_years_are_not_invented_as_paid(self):
        rows = [dict(date='Sent for payment 06.11.2026', amount='200', reference='Pending!E7'),
                dict(date='2025-11-03', amount='100', reference='Old!E3')]
        self.assertEqual(omitted_payment_months(2026, set(), {}, rows), [])
        self.assertIsNone(source_date('Planned'))

    def test_extended_coverage_removes_the_warning_and_exact_amounts_are_preserved(self):
        calendar = {19: (2026, 11)}
        rows = [dict(date='11/03/2026', amount='0.1', reference='Nov!E3'),
                dict(date='03.11.2026', amount='0.2', reference='Nov!E4')]
        self.assertEqual(omitted_payment_months(2026, set(), calendar, rows)[0]['datedAmountUah'], '0.3')
        self.assertEqual(omitted_payment_months(2026, {19}, calendar, rows), [])


if __name__ == '__main__':
    unittest.main()
