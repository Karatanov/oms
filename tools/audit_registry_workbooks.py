"""Read-only preflight for a coordinated OMS registry rebuild.

Never evaluates formulas or writes to either the source workbooks or the database.
The manifest fingerprints every input and exposes header drift and duplicate keys
before an operator can replace a production registry.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import Counter
from pathlib import Path

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter


CODE = re.compile(r"^[A-Z]{2}\d{2}_\d+(?:#LOT\d+)?$", re.I)


def inspect(path: Path) -> dict:
    result = {"file": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "sheets": []}
    with path.open("rb") as stream:
        book = load_workbook(stream, read_only=True, data_only=True)
        for sheet in book:
            # Some approved exports omit the worksheet dimension. Iterate real
            # XML rows instead of treating max_row=None as an empty worksheet.
            rows = list(sheet.iter_rows(values_only=True))
            populated = [(index, row) for index, row in enumerate(rows, 1) if any(v is not None for v in row)]
            headers = []
            for index, row in populated[:20]:
                values = [(get_column_letter(i), str(v).strip()) for i, v in enumerate(row, 1) if v is not None]
                if any(re.fullmatch(r"sub-project (?:lot )?id|id", value, re.I) for _, value in values):
                    headers.append({"row": index, "columns": dict(values)})
            codes = Counter(str(v).strip().upper() for _, row in populated for v in row
                            if isinstance(v, str) and CODE.fullmatch(v.strip()))
            result["sheets"].append({
                "sheet": sheet.title, "populated_rows": len(populated),
                "last_populated_row": populated[-1][0] if populated else 0,
                "headers": headers,
                "distinct_codes": sorted(codes),
                "formula_errors": [
                    {"cell": f"{get_column_letter(column)}{index}", "error": value}
                    for index, row in populated for column, value in enumerate(row, 1)
                    if isinstance(value, str) and value in {
                        "#REF!", "#VALUE!", "#DIV/0!", "#N/A", "#NAME?", "#NUM!"}
                ],
            })
        book.close()
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    files = sorted(args.directory.glob("*.xlsx"))
    if not files:
        parser.error("No XLSX source files found")
    print(json.dumps([inspect(path) for path in files], ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
