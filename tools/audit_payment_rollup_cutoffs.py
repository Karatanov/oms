"""Read-only checks of annual SUM coverage against dated source payments.

Never evaluates arbitrary Excel formulas, saves the workbook or updates OMS.
Only the supported single-row SUM range is interpreted; other formulas are reported.
"""
import argparse
import hashlib
import json
import re
from collections import defaultdict
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path


def column_number(value):
    result = 0
    for char in value.upper():
        result = result * 26 + ord(char) - ord('A') + 1
    return result


def annual_sum_columns(formula, row):
    if not isinstance(formula, str):
        return None
    ranges = list(re.finditer(r'(?<![A-Z_])SUM\(\s*\$?([A-Z]+)\$?(\d+)\s*:\s*\$?([A-Z]+)\$?(\d+)\s*\)', formula, re.I))
    if len(ranges) != 1:
        return None
    match = ranges[0]
    # An additional cell reference could extend coverage outside this SUM.
    if re.search(r'\$?[A-Z]{1,3}\$?\d+', formula[:match.start()] + formula[match.end():], re.I):
        return None
    start, first, end, last = match.groups()
    if int(first) != row or int(last) != row or column_number(start) > column_number(end):
        return None
    return set(range(column_number(start), column_number(end) + 1))


def source_date(value):
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    # Full-string formats only: "Sent for payment 06.11.2026" is not paid.
    for pattern in ('%m/%d/%Y', '%Y-%m-%d', '%d.%m.%Y'):
        try:
            return datetime.strptime(str(value).strip(), pattern).date()
        except ValueError:
            pass
    return None


def omitted_payment_months(year, included_columns, calendar, payments):
    coverage = {(y, month) for column, (y, month) in calendar.items() if column in included_columns}
    grouped = defaultdict(list)
    for payment in payments:
        paid_date = source_date(payment['date'])
        if paid_date and paid_date.year == year and (year, paid_date.month) not in coverage:
            grouped[paid_date.month].append(payment)
    return [dict(month=f'{year}-{month:02}', datedAmountUah=str(sum(
                (Decimal(str(row['amount'])) for row in rows), Decimal(0))),
                 sourceRows=[row['reference'] for row in rows]) for month, rows in sorted(grouped.items())]


def audit(path):
    from openpyxl import load_workbook  # Optional read-only analysis dependency, not needed by regressions.
    before = hashlib.sha256(path.read_bytes()).hexdigest()
    values = load_workbook(path, data_only=True)
    formulas = load_workbook(path, data_only=False)
    try:
        by_code = defaultdict(list)
        for sheet in values:
            if 'payments' not in sheet.title.lower():
                continue
            header = None
            for cells in sheet.iter_rows(max_row=min(20, sheet.max_row), max_col=20):
                columns = {re.sub(r'\s+', ' ', str(c.value or '').strip().lower()): c.column for c in cells}
                if {'id', 'date', 'amount, uah'} <= columns.keys():
                    header = (cells[0].row, columns)
                    break
            if header is None:
                continue
            header_row, columns = header
            for cells in sheet.iter_rows(min_row=header_row + 1, max_col=max(columns.values())):
                code = str(cells[columns['id'] - 1].value or '').strip().upper()
                paid_date = cells[columns['date'] - 1].value
                amount = cells[columns['amount, uah'] - 1].value
                if not re.fullmatch(r'[A-Z]{2}\d{2}_\d+(?:#LOT\d+)?_(?:W|TS|CSC)(?:\$\d+)?', code):
                    continue
                if source_date(paid_date) is not None and isinstance(amount, (int, float)) and not isinstance(amount, bool):
                    by_code[code].append(dict(date=paid_date, amount=amount,
                        reference=f'{sheet.title}!{cells[columns["amount, uah"] - 1].coordinate}'))
        sheet = values['UR III UAH Payment Dashboard']
        source = formulas[sheet.title]
        year_match = re.search(r'\b(20\d{2})\b', str(sheet['F2'].value))
        if not year_match:
            raise ValueError('Cannot identify the annual paid year from F2; no cutoff is assumed.')
        year = int(year_match[1])
        calendar = {}
        for column in range(8, sheet.max_column + 1):
            y, month = sheet.cell(2, column).value, sheet.cell(3, column).value
            if isinstance(y, (int, float)) and isinstance(month, (int, float)) and y == int(y) and month == int(month) and 1 <= month <= 12:
                calendar[column] = (int(y), int(month))
        omitted, unsupported = [], []
        for row in range(5, sheet.max_row + 1):
            code = str(sheet.cell(row, 1).value or '').strip().upper()
            if code not in by_code:
                continue
            formula = source.cell(row, 6).value
            included = annual_sum_columns(formula, row)
            if included is None:
                unsupported.append(dict(code=code, cell=f'F{row}'))
                continue
            missing = omitted_payment_months(year, included, calendar, by_code[code])
            if missing:
                omitted.append(dict(code=code, annualCell=f'F{row}', annualCachedUah=sheet.cell(row, 6).value,
                                    annualFormula=formula, missingDatedMonths=missing))
        return dict(sha256=before, workbookWrites=False, year=year,
                    datedRows=sum(len(rows) for rows in by_code.values()), omitted=omitted, unsupported=unsupported)
    finally:
        values.close()
        formulas.close()
        if before != hashlib.sha256(path.read_bytes()).hexdigest():
            raise RuntimeError('Source file changed during the audit.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('workbook', type=Path)
    args = parser.parse_args()
    print(json.dumps(audit(args.workbook), ensure_ascii=True, indent=2))


if __name__ == '__main__':
    main()
