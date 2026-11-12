import json
import unittest
from registry_precision_repair import plan


class PrecisionRepairTest(unittest.TestCase):
    def test_only_proven_rounding_is_changed(self):
        row = dict(id=1, code="VN08_01", workbook="DB_Monitoring table.xlsx", budget=1235, contract=None,
                   registry=json.dumps(dict(fundingTotal="1,235", constructionAmount="manual", name="unchanged", pool="8")))
        source = dict(fields=dict(fundingTotal=dict(old="1,235", new="1234.56"),
                                  constructionAmount=dict(old="1,235", new="1234.56"), pool=dict(old="8", new="9")))
        result = list(plan([row], {(row["workbook"], row["code"]): source}))
        self.assertEqual({"fundingTotal": "1234.56"}, result[0][1])
        self.assertEqual({"budget_planned": 1234}, result[0][2])
        self.assertEqual("unchanged", json.loads(row["registry"])["name"])
        self.assertEqual([], list(plan([dict(row, registry='{"fundingTotal":"1234.56"}')],
                                      {(row["workbook"], row["code"]): source})))

    def test_manual_legacy_amount_and_missing_source_are_not_overwritten(self):
        row = dict(id=1, code="VN08_01", workbook="DB_Monitoring table.xlsx", budget=999, contract=None,
                   registry='{"fundingTotal":"1,235"}')
        sources = {(row["workbook"], row["code"]): dict(fields=dict(fundingTotal=dict(old="1,235", new="1234.56")))}
        self.assertEqual({}, list(plan([row], sources))[0][2])
        self.assertEqual([], list(plan([row], {})))


if __name__ == "__main__":
    unittest.main()
