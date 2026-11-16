"""Offline regressions for the review-only budget reconciliation planner."""
import copy
import json
import unittest

from plan_budget_snapshot_repair import literal, plan, sql_plan, verify


def row(**overrides):
    result = dict(id=1, code='TEST_01', type='subproject', budget=1200, currency='UAH',
                  sourceLot='TEST_01', workbook='DB_Monitoring table.xlsx',
                  registry=json.dumps(dict(fundingTotal='1 200,25')), snapshotId=4,
                  amount='960.00', snapshotCurrency='UAH', converted='20.00',
                  rate='48.00000000', rateDate='2026-01-01', edited=0)
    return dict(result, **overrides)


class BudgetSnapshotRepairTest(unittest.TestCase):
    def test_sql_literals_preserve_ukrainian_and_quotes_without_shell_encoding(self):
        self.assertEqual(literal("грн'"), "CONVERT(X'd0b3d180d0bd27' USING utf8mb4)")

    def test_uses_frozen_rate_without_changing_legacy_rounding(self):
        changes, skipped = plan([row()])
        self.assertFalse(skipped)
        self.assertIsNone(changes[0]['newBudget'])
        self.assertEqual(changes[0]['newSnapshot'], dict(amount='1200.25', converted='25.01'))

    def test_protects_manual_original_currency_and_inconsistent_conversion(self):
        for overrides in (dict(edited=1), dict(snapshotCurrency='EUR'), dict(converted='99'),
                          dict(rate='0'), dict(rate=None), dict(amount='NaN')):
            with self.subTest(overrides=overrides):
                changes, skipped = plan([row(budget=1, **overrides)])
                self.assertFalse(changes)
                self.assertEqual(len(skipped), 1)

    def test_missing_snapshot_is_not_created(self):
        changes, _ = plan([row(snapshotId=None, budget=1)])
        self.assertEqual(changes[0]['newBudget'], 1200)
        self.assertIsNone(changes[0]['newSnapshot'])
        self.assertIn('NOT EXISTS (SELECT 1 FROM project_amounts', sql_plan(changes))

    def test_legacy_only_when_snapshot_already_matches(self):
        changes, _ = plan([row(budget=1, amount='1200.25', converted='25.01')])
        self.assertEqual(changes[0]['newBudget'], 1200)
        self.assertIsNone(changes[0]['newSnapshot'])
        self.assertIn('s.conversion_edited=0', sql_plan(changes))
        self.assertIn('p.updated_at=p.updated_at', sql_plan(changes))

    def test_unknown_source_wrong_identity_and_zero_are_not_guessed(self):
        for overrides in (dict(workbook='other.xlsx'), dict(sourceLot='OTHER'),
                          dict(currency='EUR'), dict(registry='{}'),
                          dict(registry='{"fundingTotal":"0"}')):
            self.assertEqual(plan([row(**overrides)])[0], [])

    def test_duplicate_source_population_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Ambiguous'):
            plan([row(), row()])

    def test_plan_is_idempotent_and_verifier_rejects_unrelated_changes(self):
        before = [row()]
        changes, _ = plan(before)
        after = copy.deepcopy(before)
        after[0].update(changes[0]['newSnapshot'])
        self.assertEqual(verify(before, after, dict(changes=changes)), (1, 1))
        self.assertEqual(plan(after)[0], [])
        after[0]['rateDate'] = '2026-02-01'
        with self.assertRaises(AssertionError):
            verify(before, after, dict(changes=changes))

    def test_sql_has_exact_source_guards_and_transaction_count_assertion(self):
        sql = sql_plan(plan([row()])[0])
        for fragment in ('START TRANSACTION', 'BINARY d.registry_json', 'a.conversion_edited=0',
                         'a.uah_per_eur=48.00000000', '@snapshots<>1', 'COMMIT'):
            self.assertIn(fragment, sql)
        self.assertNotIn('SET a.uah_per_eur', sql)
        self.assertNotIn('DELETE', sql)


if __name__ == '__main__':
    unittest.main()
