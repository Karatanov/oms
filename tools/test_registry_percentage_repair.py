import json
import unittest
from registry_percentage_repair import equivalent, plan, sql_plan
from verify_percentage_repair import verify


class PercentageRepairTest(unittest.TestCase):
    def row(self, value='697 192,92'):
        return dict(id=1, code='MY08_02', sourceLot='MY08_02', workbook='DB_Monitoring table.xlsx',
                    registry=json.dumps(dict(completedWorkPct=value, name='Preserve', fundingTotal='123.45')),
                    completedWorkPct=value, financingUsedPct=None, paidFinancingPct=None)

    def test_blank_clears_both_projections_not_money_or_other_columns(self):
        repairs = list(plan([self.row()], {'MY08_02': {'completedWorkPct': dict(value='', cell='CC106')}}))
        self.assertEqual({'completedWorkPct': ''}, repairs[0][1])
        self.assertEqual({'completed_work_pct': None}, repairs[0][2])
        sql = sql_plan(repairs)
        self.assertIn('completed_work_pct=NULL', sql)
        self.assertIn('BINARY registry_json=BINARY', sql)
        self.assertIn('WHERE @changed<>1', sql)
        self.assertNotIn('UPDATE projects', sql)

    def test_matching_fraction_keeps_original_text_and_idempotency(self):
        self.assertTrue(equivalent('0.2344', '23.44%'))
        self.assertFalse(equivalent('0%', '0.5%'))
        self.assertFalse(equivalent('0%', ''))
        self.assertFalse(equivalent('invalid', ''))
        self.assertEqual([], list(plan([self.row('23.44%')], {'MY08_02': {'completedWorkPct': dict(value='23.44%', cell='CC106')}})))

    def test_missing_source_and_ambiguous_project_mapping_are_not_updated(self):
        row = self.row()
        self.assertEqual([], list(plan([row], {})))
        row['sourceLot'] = 'DIFFERENT'
        self.assertEqual([], list(plan([row], {'MY08_02': {'completedWorkPct': dict(value='', cell='CC106')}})))

    def test_post_repair_verifier_rejects_unrelated_changes(self):
        before = self.row()
        after = dict(before, completedWorkPct=None, registry=json.dumps(dict(completedWorkPct='', name='Preserve', fundingTotal='123.45')))
        manifest = {'changes': [dict(id=1, fields=[dict(field='completedWorkPct', new='')])]}
        self.assertEqual((1, 1), verify([before], [after], manifest))
        after['registry'] = json.dumps(dict(completedWorkPct='', name='Changed', fundingTotal='123.45'))
        with self.assertRaises(AssertionError):
            verify([before], [after], manifest)


if __name__ == '__main__':
    unittest.main()
