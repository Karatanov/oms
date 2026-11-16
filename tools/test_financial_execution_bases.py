"""Offline checks of the independent read-only denominator audit."""
import json
import unittest
from audit_financial_execution_bases import execution_bases


def project(identifier, code, kind='subproject', parent=None, source=None, contract=None, budget=100):
    return dict(kind='project', id=identifier, code=code, type=kind, parent=parent, status='active',
                registry=json.dumps(dict(constructionAmount=source)), contract=contract, budget=budget)


class ExecutionBaseAuditTest(unittest.TestCase):
    def test_parent_and_lots_are_never_added_together(self):
        rows = [project(1, 'SP', source='10'), project(2, 'SP#Lot1', 'subproject_part', 1, '3')]
        self.assertEqual(execution_bases(rows)[0]['baseUah'], '10')
        self.assertEqual(execution_bases(rows)[0]['provenance'], 'parent_source')

    def test_lot_precedence_and_partial_source_coverage_are_explicit(self):
        rows = [project(1, 'SP', contract=500), project(2, 'SP#Lot1', 'subproject_part', 1, '0.1'),
                project(3, 'SP#Lot2', 'subproject_part', 1, '0.2'),
                project(4, 'SP#Lot3', 'subproject_part', 1)]
        result = execution_bases(rows)[0]
        self.assertEqual(result['baseUah'], '0.3')
        self.assertEqual(result['provenance'], 'lot_sources')
        self.assertEqual((result['knownLotAmounts'], result['totalParts']), (2, 3))

    def test_fallbacks_and_missing_base_are_not_fabricated_as_source(self):
        rows = [project(1, 'A', contract=30), project(2, 'B', budget=20), project(3, 'C', budget=0)]
        self.assertEqual([r['provenance'] for r in execution_bases(rows)],
                         ['stored_contract', 'approved_budget', 'unavailable'])


if __name__ == '__main__':
    unittest.main()
