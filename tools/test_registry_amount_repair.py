import unittest

from registry_amount_repair import amount, broken_amount, plan, sql_text


class AmountRepairTest(unittest.TestCase):
    def test_currency_repair_requires_a_matching_approved_budget(self):
        row = dict(id=1, code="VN08_01", budget=1234567, contract=None, currency="EUR",
                   source_budget="1,234,567.89", source_contract=None)
        self.assertEqual({"currency": "UAH"}, list(plan([row], True))[0][1])
        self.assertEqual([], list(plan([dict(row, currency="UAH")], True)))
        self.assertEqual([], list(plan([dict(row, budget=999)], True)))
        self.assertEqual([], list(plan([dict(row, currency="USD")], True)))

    def test_sql_literals_do_not_interpolate_source_text(self):
        self.assertEqual("NULL", sql_text(None))
        self.assertEqual("CONVERT(X'' USING utf8mb4)", sql_text(""))
        self.assertNotIn("' OR 1=1", sql_text("' OR 1=1"))

    def test_grouped_and_decimal_values(self):
        for text in ("1,234,567.89", "1 234 567,89", "1\u00a0234\u00a0567,89"):
            self.assertEqual(1234567, amount(text))
        self.assertEqual(6484316, amount("6,484,316"))
        self.assertEqual(888, broken_amount("888,956"))
        self.assertEqual(888956, amount("888,956"))
        for text in (None, "", "#VALUE!", "Infinity", "NaN", "-1"):
            self.assertIsNone(amount(text))

    def test_only_proven_legacy_values_are_repaired(self):
        row = dict(id=1, code="VN08_01", budget=0, contract=None,
                   source_budget="1,234,567.89", source_contract="1,312,885")
        changes = list(plan([row]))[0][1]
        self.assertEqual(dict(budget_planned=1234567, subproject_contract_amount=1312885), changes)
        self.assertEqual([], list(plan([dict(row, budget=987, contract=654)])))
        self.assertEqual([], list(plan([dict(row, budget=1234567, contract=1312885)])))


if __name__ == "__main__":
    unittest.main()
