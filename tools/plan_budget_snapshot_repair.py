"""Prepare guarded budget reconciliation SQL only; this tool never connects to the DB."""
import argparse
import hashlib
import json
from collections import Counter
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP
from pathlib import Path
from audit_budget_representations import source_money

WORKBOOKS = {'DB_Monitoring table.xlsx', 'UR III AT&PP Allocation Table.xlsx'}
CENT = Decimal('0.01')


def literal(value):
    if value is None:
        return 'NULL'
    return "CONVERT(X'" + str(value).encode('utf-8').hex() + "' USING utf8mb4)"


def plan(rows):
    counts = Counter(row['id'] for row in rows)
    changes, skipped = [], []
    for row in rows:
        if counts[row['id']] != 1:
            raise ValueError('Ambiguous budget/source rows for project ' + str(row['id']))
        if row['workbook'] not in WORKBOOKS or (row['sourceLot'] or '').strip().upper() != row['code'].strip().upper():
            continue
        source = source_money(json.loads(row['registry'] or '{}').get('fundingTotal'))
        if source is None or source <= 0 or source >= Decimal('1e16') or row['currency'] != 'UAH':
            continue
        rounded = source.quantize(CENT, rounding=ROUND_HALF_UP)
        legacy = int(source)  # Match the established positive-UAH importer truncation policy.
        legacy_change = legacy if abs(source - Decimal(row['budget'])) > Decimal('1.01') else None
        saved_change = None
        if row['snapshotId'] is not None:
            try:
                old, converted, rate = (Decimal(row[key]) for key in ('amount', 'converted', 'rate'))
                if not all(value.is_finite() for value in (old, converted, rate)) or rate <= 0:
                    raise ValueError('invalid saved conversion')
                if row['snapshotCurrency'] != 'UAH' or row['edited']:
                    raise ValueError('original currency or manual conversion is protected')
                expected_old = (old / rate).quantize(CENT, rounding=ROUND_HALF_UP)
                if abs(expected_old - converted) > CENT:
                    raise ValueError('saved conversion does not match its frozen rate')
                if abs(old - rounded) >= CENT:
                    new_converted = (rounded / rate).quantize(CENT, rounding=ROUND_HALF_UP)
                    if new_converted >= Decimal('1e16'):
                        raise ValueError('converted amount exceeds column precision')
                    saved_change = dict(amount=str(rounded), converted=str(new_converted))
            except (InvalidOperation, ValueError, TypeError) as error:
                skipped.append(dict(code=row['code'], reason=str(error)))
                # Do not partially align a project whose snapshot is protected or ambiguous.
                continue
        if legacy_change is not None or saved_change is not None:
            changes.append(dict(before=row, sourceUah=str(source), newBudget=legacy_change, newSnapshot=saved_change))
    return changes, skipped


def sql_plan(changes):
    lines = ['CREATE TEMPORARY TABLE oms_budget_assertion (id INT PRIMARY KEY);',
             'INSERT INTO oms_budget_assertion VALUES (1);', 'START TRANSACTION; SET @budgets=0; SET @snapshots=0;']
    for change in changes:
        row = change['before']
        guard = ('p.id=' + str(int(row['id'])) + ' AND BINARY p.site_number=BINARY ' + literal(row['code'])
                 + ' AND p.currency=' + literal(row['currency']) + ' AND p.budget_planned=' + str(int(row['budget']))
                 + ' AND d.source_workbook=' + literal(row['workbook']) + ' AND BINARY d.source_lot_id=BINARY ' + literal(row['sourceLot'])
                 + ' AND BINARY d.registry_json=BINARY ' + literal(row['registry']))
        if change['newSnapshot'] is not None:
            new = change['newSnapshot']
            lines += ['UPDATE project_amounts a JOIN projects p ON p.id=a.project_id JOIN project_monitoring_details d ON d.project_id=p.id'
                      + ' SET a.amount=' + new['amount'] + ',a.converted_amount=' + new['converted']
                      + ' WHERE ' + guard + ' AND a.id=' + str(int(row['snapshotId'])) + " AND a.amount_kind='budget'"
                      + ' AND a.currency=' + literal(row['snapshotCurrency']) + ' AND a.amount=' + row['amount']
                      + ' AND a.converted_amount=' + row['converted'] + ' AND a.uah_per_eur=' + row['rate']
                      + ' AND a.rate_date=' + literal(row['rateDate']) + ' AND a.conversion_edited=0;',
                      'SET @snapshots=@snapshots+ROW_COUNT();']
        if change['newBudget'] is not None:
            # Recheck the entire saved snapshot, including the absence of one. A
            # concurrent manual FX edit must not leave a partially aligned project.
            snapshot_guard = " AND NOT EXISTS (SELECT 1 FROM project_amounts s WHERE s.project_id=p.id AND s.amount_kind='budget')"
            if row['snapshotId'] is not None:
                saved = change['newSnapshot'] or row
                snapshot_guard = (" AND EXISTS (SELECT 1 FROM project_amounts s WHERE s.project_id=p.id AND s.amount_kind='budget'"
                                  + ' AND s.id=' + str(int(row['snapshotId']))
                                  + ' AND s.amount=' + saved['amount'] + ' AND s.converted_amount=' + saved['converted']
                                  + ' AND s.currency=' + literal(row['snapshotCurrency'])
                                  + ' AND s.uah_per_eur=' + row['rate']
                                  + ' AND s.rate_date <=> ' + literal(row['rateDate']) + ' AND s.conversion_edited=0)')
            lines += ['UPDATE projects p JOIN project_monitoring_details d ON d.project_id=p.id'
                      + ' SET p.budget_planned=' + str(change['newBudget'])
                      # Historical reconciliation is not a fresh source import.
                      + ',p.updated_at=p.updated_at WHERE ' + guard + snapshot_guard + ';',
                      'SET @budgets=@budgets+ROW_COUNT();']
    budgets = sum(change['newBudget'] is not None for change in changes)
    snapshots = sum(change['newSnapshot'] is not None for change in changes)
    lines += ['INSERT INTO oms_budget_assertion SELECT 1 WHERE @budgets<>' + str(budgets) + ' OR @snapshots<>' + str(snapshots) + ';',
              'SELECT @budgets AS aligned_legacy_budgets,@snapshots AS aligned_budget_snapshots; COMMIT;']
    return '\n'.join(lines) + '\n'


def verify(before, after, manifest):
    old, new = ({row['id']: row for row in values} for values in (before, after))
    assert len(old) == len(before) and len(new) == len(after), 'Ambiguous row population'
    assert old.keys() == new.keys(), 'Project population changed'
    allowed = {change['before']['id']: change for change in manifest['changes']}
    for identifier, previous in old.items():
        current, change = new[identifier], allowed.get(identifier)
        expected = dict(previous)
        if change:
            if change['newBudget'] is not None:
                expected['budget'] = change['newBudget']
            if change['newSnapshot'] is not None:
                expected.update(change['newSnapshot'])
        for key in previous:
            if key in ('amount', 'converted') and expected[key] is not None:
                assert Decimal(current[key]) == Decimal(expected[key]), (identifier, key)
            else:
                assert current[key] == expected[key], (identifier, key)
    return len(old), len(allowed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('export', type=Path)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--verify-after', type=Path)
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.export.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    changes, skipped = plan(rows)
    manifest = dict(exportSha256=hashlib.sha256(args.export.read_bytes()).hexdigest(), changes=changes, skipped=skipped)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    (args.output_dir / 'budget-repair.sql').write_text(sql_plan(changes), encoding='utf-8')
    (args.output_dir / 'budget-repair-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    summary = dict(exportedRows=len(rows), plannedProjects=len(changes),
                   legacyBudgets=sum(c['newBudget'] is not None for c in changes),
                   budgetSnapshots=sum(c['newSnapshot'] is not None for c in changes), skipped=skipped,
                   databaseWrites=False)
    if args.verify_after:
        after = [json.loads(line) for line in args.verify_after.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
        summary['verified'] = verify(rows, after, manifest)
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
