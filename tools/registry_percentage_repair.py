"""Generate (never execute) an optimistic, atomic repair of source-owned percentage fields."""
import argparse
import hashlib
import json
from decimal import Decimal, InvalidOperation
from pathlib import Path
from registry_amount_repair import sql_text

FIELDS = {'approvedFundingPct': 39, 'completedWorkPct': 80, 'financingUsedPct': 81, 'paidFinancingPct': 82}
COLUMNS = {'completedWorkPct': 'completed_work_pct', 'financingUsedPct': 'financing_used_pct', 'paidFinancingPct': 'paid_financing_pct'}


def fraction(value):
    if value is None or not str(value).strip():
        return None
    raw = ''.join(str(value).split()).replace(',', '.')
    try:
        number = Decimal(raw.replace('%', ''))
        if not number.is_finite() or number < 0:
            return None
        return number / 100 if '%' in raw or number > 1 else number
    except InvalidOperation:
        return None


def equivalent(actual, expected):
    if expected == '':
        return actual is None or str(actual).strip() == ''
    left, right = fraction(actual), fraction(expected)
    return left is not None and right is not None and abs(left-right) <= Decimal('0.0000000001')


def read_source(path):
    from openpyxl import load_workbook
    cached = load_workbook(path, read_only=False, data_only=True)
    formulas = load_workbook(path, read_only=False, data_only=False)
    sheet, formula_sheet = cached['DB Table'], formulas['DB Table']
    headers = {str(c.value).strip(): c.column-1 for c in sheet[12] if c.value is not None}
    groups = {}
    for row in sheet.iter_rows(min_row=16):
        sp = str(row[headers['Sub-Project ID']].value or '').strip()
        if not sp:
            continue
        code = str(row[headers['Sub-Project Lot ID']].value or sp).strip().upper()
        groups.setdefault(code, []).append(row)
    result, skipped = {}, []
    for code, rows in groups.items():
        fields = {}
        for field, column in FIELDS.items():
            first, last = rows[0][column], rows[-1][column]
            cell = last if last.value is not None and str(last.value).strip() else first
            if cell.value is None and formula_sheet[cell.coordinate].data_type == 'f':
                # No cached result cannot prove that a formula legitimately evaluates to blank.
                skipped.append({'code': code, 'field': field, 'cell': cell.coordinate, 'reason': 'formula without cached value'})
                continue
            if cell.value is None or str(cell.value).strip() == '':
                value = ''
            elif isinstance(cell.value, (int, float)) and not isinstance(cell.value, bool):
                number = Decimal(str(cell.value))
                value = str(cell.value)
                # Strip zeros only after the decimal point, never from an integer like 100.
                if '%' in cell.number_format:
                    value = format(number * 100, 'f')
                    value = (value.rstrip('0').rstrip('.') if '.' in value else value) + '%'
            else:
                value = str(cell.value).strip()
            if value and fraction(value) is None:
                skipped.append({'code': code, 'field': field, 'cell': cell.coordinate, 'reason': 'invalid source percentage'})
                continue
            fields[field] = {'value': value, 'cell': cell.coordinate}
        result[code] = fields
    cached.close()
    formulas.close()
    return result, skipped


def plan(rows, sources):
    for row in rows:
        if row['workbook'] != 'DB_Monitoring table.xlsx' or row['code'].strip().upper() != row['sourceLot'].strip().upper():
            continue
        source = sources.get(row['code'].strip().upper())
        if source is None:
            continue
        registry = json.loads(row['registry'])
        payload, columns, evidence = {}, {}, []
        for field, cell in source.items():
            value = cell['value']
            if not equivalent(registry.get(field), value):
                payload[field] = value
            if field in COLUMNS and not equivalent(row.get(field), value):
                columns[COLUMNS[field]] = value or None
            if field in payload or COLUMNS.get(field) in columns:
                evidence.append({'field': field, 'cell': cell['cell'], 'new': value,
                                 'registryBefore': registry.get(field), 'typedBefore': row.get(field)})
        if payload or columns:
            yield row, payload, columns, evidence


def sql_plan(repairs):
    statements = ['CREATE TEMPORARY TABLE oms_percentage_assertion (id INT PRIMARY KEY);',
                  'INSERT INTO oms_percentage_assertion VALUES (1);', 'START TRANSACTION; SET @changed=0;']
    for row, payload, columns, _ in repairs:
        assignments = [column+'='+sql_text(value) for column, value in columns.items()]
        if payload:
            pairs = ','.join(sql_text('$.'+key)+','+sql_text(value) for key, value in payload.items())
            assignments.append('registry_json=JSON_SET(registry_json,'+pairs+')')
        guards = ['project_id='+str(int(row['id'])),
                  'EXISTS (SELECT 1 FROM projects p WHERE p.id=project_id AND BINARY p.site_number=BINARY '+sql_text(row['code'])+')',
                  'BINARY source_lot_id=BINARY '+sql_text(row['sourceLot']),
                  'BINARY source_workbook=BINARY '+sql_text(row['workbook']),
                  'BINARY registry_json=BINARY '+sql_text(row['registry'])]
        guards += ['(BINARY '+column+' <=> BINARY '+sql_text(row.get(field))+')' for field, column in COLUMNS.items()]
        statements += ['UPDATE project_monitoring_details SET '+','.join(assignments)+' WHERE '+' AND '.join(guards)+';',
                       'SET @changed=@changed+ROW_COUNT();']
    statements += ['INSERT INTO oms_percentage_assertion SELECT 1 WHERE @changed<>'+str(len(repairs))+';',
                   'SELECT @changed AS corrected_percentage_rows; COMMIT;']
    return '\n'.join(statements)+'\n'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('workbook', type=Path)
    parser.add_argument('database_export', type=Path)
    parser.add_argument('--output-dir', type=Path)
    args = parser.parse_args()
    sources, skipped = read_source(args.workbook)
    rows = [json.loads(line) for line in args.database_export.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    repairs = list(plan(rows, sources))
    result = {'sourceSha256': hashlib.sha256(args.workbook.read_bytes()).hexdigest(),
                      'summary': {'exportedRows': len(rows), 'sourceRows': len(sources), 'repairRows': len(repairs),
                                  'registryFields': sum(len(p) for _, p, _, _ in repairs),
                                  'typedFields': sum(len(c) for _, _, c, _ in repairs)},
                      'changes': [{'code': r['code'], 'id': r['id'], 'fields': e} for r, _, _, e in repairs],
                      'skippedSourceCells': skipped}
    sql = sql_plan(repairs)
    if args.output_dir:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / 'percentage-repair.sql').write_text(sql, encoding='utf-8')
        (args.output_dir / 'percentage-repair-manifest.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'summary': result['summary'], 'sourceSha256': result['sourceSha256'],
                          'skippedSourceCells': skipped, 'changes': result['changes']}, ensure_ascii=False))
    else:
        print(json.dumps(dict(result, sql=sql), ensure_ascii=False))


if __name__ == '__main__':
    main()
