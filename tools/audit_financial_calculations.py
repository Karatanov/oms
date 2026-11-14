"""Independent, read-only reconciliation of OMS API and persisted source amounts.

No imports, migrations or record updates. Tokens remain in memory and are never
included in output. Decimal arithmetic is independent of the Kotlin aggregates.
"""
import json
import re
import subprocess
import urllib.request
import sys
import zipfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from decimal import Decimal
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    exported = subprocess.run(
        ['ssh', '-i', str(Path.home() / '.ssh/oms_vps_codex'), '-o', 'BatchMode=yes',
         'root@2.24.162.154', "tr -d '\\r' | bash"],
        input=(ROOT / 'deploy/vps/export-calculation-audit.sh').read_bytes(),
        capture_output=True, check=True)
    rows = [json.loads(line, parse_float=Decimal) for line in exported.stdout.decode().splitlines()]
    groups = {kind: [r for r in rows if r['kind'] == kind]
              for kind in ('project', 'payment', 'transaction', 'procurement')}
    def total(values):
        return sum((Decimal(str(v)) for v in values if v is not None), Decimal(0))
    def key(code):
        return re.sub(r'_(W|TS|CSC)$', '', code.split('$')[0], flags=re.I).split('#')[0].strip().upper()
    def component(code):
        part = code.split('$')[0].split('_')[-1].upper()
        return part if part in ('W', 'TS', 'CSC') else None
    def request(path, token=None, post=False):
        headers = {'Cache-Control': 'no-cache'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('https://ua-oms.com' + path, data=b'' if post else None, headers=headers)
        with urllib.request.urlopen(req, timeout=90) as response:
            return json.load(response, parse_float=Decimal)
    token = request('/api/v1/auth/guest', post=True)['accessToken']
    overview = request('/api/v1/dashboard/overview', token)
    active = [p for p in groups['project'] if p['type'] == 'subproject' and p['status'] == 'active']
    codes = {key(p['code']) for p in active}
    transactions = groups['transaction']
    primary = [r for r in groups['payment'] if component(r['code']) is None]
    checks = []
    def check(name, actual, expected, tolerance=Decimal('0.011')):
        ok = abs(Decimal(str(actual))-Decimal(str(expected))) <= tolerance
        checks.append({'check': name, 'ok': ok, 'actual': str(actual), 'expected': str(expected)})
    summary = overview['nationalSummary']
    check('active subproject count', summary['subprojectsCount'], len(active), 0)
    check('cumulative primary paid (not multiplied by execution ratio)', summary['eibPaidUnderContractsUah'],
          total(r['paid'] for r in primary if key(r['code']) in codes))
    check('funding card = sum of all map source rows UAH', summary['approvedFundingUah'],
          total(r['amountUah'] for r in overview['subprojectFunding']))
    check('funding card = sum of all map source rows EUR', summary['approvedFundingEur'],
          total(r['amountEur'] for r in overview['subprojectFunding']))
    check('monthly UAH = dated transaction total',
          total(r['amountUahCents'] for r in overview['monthlyActPayments']) / 100,
          total(r['uah'] for r in transactions))
    check('monthly EUR = dated transaction total',
          total(r['amountEurCents'] for r in overview['monthlyActPayments']) / 100,
          total(r['eur'] for r in transactions))
    signed = [p for p in groups['procurement'] if any(s in (p['status'] or '').lower()
                                                    for s in ('договір укладено', 'contract signed'))]
    unique_signed = {}
    for p in signed:
        unique_signed.setdefault(p['procurement'] or p['tender'] or 'record-' + str(p['id']), p)
    check('signed contract count', summary['signedContractsCount'], len(unique_signed), 0)
    check('signed contract amount', summary['signedContractsAmountUah'],
          total(p['signed'] if p['signed'] is not None else p['amount'] for p in unique_signed.values()))
    monthly = defaultdict(Decimal)
    for r in transactions:
        monthly[r['date'][:7]] += r['uah']
    for row in overview['monthlyActPayments']:
        check('month ' + row['month'], Decimal(row['amountUahCents']) / 100, monthly[row['month']])
    source_by_code = {key(r['code']): r for r in primary}
    for row in overview['subprojectPaymentProgress']:
        source = source_by_code[row['code']]
        if source['eib'] and source['eib'] > 0 and source['paid'] is not None:
            check('payment ratio ' + row['code'], row['paymentPct'], source['paid'] / source['eib'] * 100)
    for tranche, batches in [(1, (1, 8)), (2, (2, 9))]:
        scoped = request('/api/v1/dashboard/overview?tranche=' + str(tranche), token)
        check('tranche monthly ' + str(tranche),
              total(r['amountUahCents'] for r in scoped.get('monthlyActPayments', [])) / 100,
              total(r['uah'] for r in transactions if r['batch'] in batches))
    discrepancies = []
    for r in groups['payment']:
        for name, parts, result in [('investment split', ('eib', 'local'), 'total'),
                                    ('remaining = EIB minus paid/in-progress', ('eib', 'inProgress'), 'remaining')]:
            if all(r.get(k) is not None for k in (*parts, result)):
                expected = r[parts[0]] + r[parts[1]] if name == 'investment split' else r[parts[0]] - r[parts[1]]
                if abs(expected-r[result]) > Decimal('0.02'):
                    discrepancies.append({'code': r['code'], 'rule': name,
                                          'actual': str(r[result]), 'expected': str(expected)})
    by_type = {kind: str(total(r['uah'] for r in transactions if component(r['code']) == kind))
               for kind in ('W', 'TS', 'CSC', None)}
    dated_by_code = defaultdict(Decimal)
    for r in transactions:
        dated_by_code[key(r['code'])] += r['uah']
    cumulative_vs_dated = [
        {'code': key(r['code']), 'primaryPaid': str(r['paid']),
         'datedTotal': str(dated_by_code[key(r['code'])]),
         'difference': str(dated_by_code[key(r['code'])] - (r['paid'] or 0))}
        for r in primary if
        abs(dated_by_code[key(r['code'])] - (r['paid'] or 0)) > Decimal('0.011')]
    def source_amount(p):
        raw = json.loads(p['registry'] or '{}').get('constructionAmount', '')
        normalized = re.sub(r'\s|\u00a0', '', raw)
        if ',' in normalized and '.' in normalized or re.fullmatch(r'-?\d{1,3}(,\d{3})+', normalized):
            normalized = normalized.replace(',', '')
        else:
            normalized = normalized.replace(',', '.')
        try:
            value = Decimal(normalized)
            return value if value.is_finite() and value > 0 else None
        except Exception:
            return None
    def base(p):
        parent = source_amount(p)
        lots = total(source_amount(part) for part in groups['project']
                     if part['type'] == 'subproject_part' and part['parent'] == p['id'])
        return next((v for v in (parent, lots, p['contract'], p['budget']) if v is not None and v > 0), Decimal(0))
    execution_scope = {}
    for label, projects in [('active', active), ('all_subprojects', [p for p in groups['project'] if p['type'] == 'subproject'])]:
        denominator = total(base(p) for p in projects)
        execution_scope[label] = {'count': len(projects), 'baseUah': str(denominator),
                                 'worksPaidUah': by_type['W'],
                                 'ratioPct': str(Decimal(by_type['W']) / denominator * 100) if denominator else None}
    output = {
        'revision': request('/version'),
        'counts': {k: len(v) for k, v in groups.items()},
        'activeSubprojects': len(active), 'allSubprojectStatuses': dict(Counter(p['status'] for p in groups['project'] if p['type'] == 'subproject')),
        'transactionTotalUah': str(total(r['uah'] for r in transactions)),
        'transactionTotalsByType': {str(k): v for k, v in by_type.items()},
        'primaryPaidTotalUah': str(total(r['paid'] for r in primary)),
        'sourceE4': sorted({str(r['control']) for r in groups['payment']}),
        'unmatchedTransactionCodes': sorted({r['code'] for r in transactions if key(r['code']) not in codes}),
        'missingClassifications': [p['code'] for p in active if not p['sector'] or not p['constructionType']],
        'summary': summary,
        'checksPassed': sum(c['ok'] for c in checks), 'checksTotal': len(checks),
        'failedChecks': [c for c in checks if not c['ok']],
        'sourceArithmeticDiscrepancies': discrepancies,
        'cumulativeVsDatedDifferences': cumulative_vs_dated,
        'financialProgressScopeComparison': execution_scope,
    }
    if len(sys.argv) > 1:
        from openpyxl import load_workbook
        directory = Path(sys.argv[1])
        path = directory / 'UR III Payments.xlsx'
        book = load_workbook(path, read_only=False, data_only=True)
        formulas = load_workbook(path, read_only=False, data_only=False)
        sheet = book['UR III SP Payment Monitoring']
        source_rows = {str(r[0].value).strip(): r for r in sheet.iter_rows(min_row=5)
                       if r[0].value is not None}
        fields = {'total': 1, 'eib': 2, 'local': 3, 'inProgress': 5, 'paid': 6,
                  'remaining': 7, 'plannedPct': 8, 'actualPct': 9}
        differences = []
        numeric_checks = 0
        for item in groups['payment']:
            source = source_rows.get(item['code'])
            if source is None:
                differences.append({'code': item['code'], 'missingSourceRow': True})
                continue
            for field, index in fields.items():
                cell = source[index]
                if isinstance(cell.value, (int, float)):
                    expected = Decimal(str(cell.value))
                    if field.endswith('Pct') and '%' in cell.number_format:
                        expected *= 100
                    numeric_checks += 1
                    if item[field] is None or abs(expected - item[field]) > Decimal('0.0051'):
                        differences.append({'code': item['code'], 'cell': cell.coordinate,
                                            'field': field, 'source': str(expected), 'db': str(item[field])})
        source_ratios = []
        for code, row in source_rows.items():
            if component(code) is not None:
                continue
            for numerator, denominator, result in [(2, 1, 8), (5, 1, 9)]:
                a, b, c = (row[i].value for i in (numerator, denominator, result))
                if all(isinstance(v, (int, float)) for v in (a, b, c)) and b:
                    delta = Decimal(str(a)) / Decimal(str(b)) - Decimal(str(c))
                    if abs(delta) > Decimal('0.000001'):
                        source_ratios.append({'code': code, 'cell': row[result].coordinate, 'deltaFraction': str(delta)})
        known_refs = {r['ref'] for r in transactions}
        omitted = []
        pending = []
        for monthly_sheet in book:
            if 'payments' not in monthly_sheet.title.lower():
                continue
            for row in monthly_sheet.iter_rows(min_row=3, max_col=5):
                code, date, amount = row[2].value, row[3].value, row[4].value
                if not isinstance(code, str) or not re.match(r'^[A-Z]{2}\d{2}_\d+', code):
                    continue
                if not isinstance(amount, (int, float)) or date is None:
                    continue
                reference = monthly_sheet.title + ':' + str(row[2].row)
                if reference not in known_refs:
                    evidence = {'code': code, 'sheet': monthly_sheet.title, 'dateCell': row[3].coordinate,
                                'rawDate': str(date), 'amountCell': row[4].coordinate, 'amountUah': str(amount)}
                    try:
                        # Do not interpret "Sent for payment" or "Planned" as paid.
                        parsed = date if isinstance(date, datetime) else datetime.strptime(str(date), '%m/%d/%Y')
                    except (ValueError, TypeError):
                        pending.append(evidence)
                    else:
                        evidence['parsedDate'] = parsed.date().isoformat()
                        omitted.append(evidence)
        dashboard = book['UR III UAH Payment Dashboard']
        formula = formulas['UR III UAH Payment Dashboard']['E4'].value
        refs = re.findall(r'E\d+', formula)
        calculated = total(dashboard[ref].value for ref in refs if isinstance(dashboard[ref].value, (int, float)))
        output['workbookCheck'] = {'numericFieldsCompared': numeric_checks, 'sourceVsDbDifferences': differences,
                                  'sourceRatioDifferences': source_ratios, 'e4Stored': dashboard['E4'].value,
                                  'e4SumOfFormulaPrecedents': str(calculated), 'e4FormulaTermCount': len(refs),
                                  'sourcePaymentsAbsentFromDb': omitted,
                                  'omittedAmountUah': str(total(r['amountUah'] for r in omitted)),
                                  'nonPaymentDateRowsExcluded': pending}
        errors = []
        namespace = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
        for workbook in directory.glob('*.xlsx'):
            with zipfile.ZipFile(workbook) as archive:
                for name in archive.namelist():
                    if not re.fullmatch(r'xl/worksheets/sheet\d+\.xml', name):
                        continue
                    root = ET.fromstring(archive.read(name))
                    counts = Counter(c.findtext(namespace+'v') for c in root.iter(namespace+'c') if c.get('t') == 'e')
                    if counts:
                        errors.append({'file': workbook.name, 'sheetXml': name, 'errors': dict(counts)})
        output['cachedWorkbookErrors'] = errors
        book.close()
        formulas.close()
    print(json.dumps(output, ensure_ascii=False, indent=2, default=str))
    if any(not c['ok'] for c in checks):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
