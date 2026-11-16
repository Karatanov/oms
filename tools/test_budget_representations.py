import unittest
from decimal import Decimal
from audit_budget_representations import classify, source_money


class BudgetRepresentationsTest(unittest.TestCase):
    def project(self, source='1000.25', legacy=1000, saved='1000.25', currency='UAH'):
        return dict(siteNumber='TEST', budgetPlanned=legacy, registry={'fundingTotal': source},
                    amounts={'budget': dict(amount=saved, convertedAmount='1000.25', currency=currency, conversionEdited=False)})

    def test_grouping_decimal_formats_zero_and_missing(self):
        for text in ('1 234 567,89', '1,234,567.89', '1.234.567,89', '+1.234.567,89'):
            self.assertEqual(Decimal('1234567.89'), source_money(text))
        self.assertEqual(Decimal(0), source_money(0))
        self.assertIsNone(source_money(None))
        self.assertIsNone(source_money('NaN'))

    def test_ambiguous_grouping_is_not_repaired_as_a_guessed_budget(self):
        for text in ('12,34.56', '12.34,56', '1,234.5.6'):
            self.assertIsNone(source_money(text))
        self.assertEqual(Decimal('0.995'), source_money('0,995'))
        self.assertEqual(Decimal('1234567'), source_money('1.234.567'))
        self.assertIsNone(source_money('-1.234,56'))

    def test_whole_hryvnia_legacy_is_not_a_false_monetary_conflict(self):
        self.assertEqual('consistent_at_legacy_precision', classify(self.project())[0])

    def test_independent_conflicts_are_classified_not_automatically_repaired(self):
        self.assertEqual('snapshot_differs_only', classify(self.project(saved='900'))[0])
        self.assertEqual('legacy_differs_only', classify(self.project(legacy=900))[0])
        self.assertEqual('both_differ_from_source', classify(self.project(legacy=900, saved='800'))[0])

    def test_eur_snapshot_comparison_uses_its_saved_uah_not_raw_euro(self):
        self.assertEqual('consistent_at_legacy_precision', classify(self.project(saved='20', currency='EUR'))[0])

    def test_missing_raw_source_is_not_replaced_by_an_api_or_legacy_guess(self):
        row = self.project()
        row['registry'] = {}
        self.assertEqual('no_source_budget', classify(row)[0])
        row = self.project()
        row['amounts'] = {}
        self.assertEqual('no_saved_snapshot', classify(row)[0])


if __name__ == '__main__':
    unittest.main()
