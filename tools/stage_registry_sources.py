"""Generate a lossless, isolated MySQL staging snapshot of approved XLSX files.

Only oms_registry_staging is written. No live OMS table is selected or modified.
Cached values, formulas, error markers and actual hyperlink targets are retained
so the later typed import can reconcile every value to a source cell.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import date, datetime
from pathlib import Path

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter


FILES = (
    "DB_Monitoring table.xlsx",
    "DB-Procurement Tables.xlsx",
    "UR III AT&PP - 125 +71 SPs - 20260831 - final.xlsx",
    "UR III Payments.xlsx",
)


def sql_text(value: str) -> str:
    # Hex literals avoid SQL-mode, apostrophe and backslash differences.
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)" if value else "''"


def typed(value: object) -> object:
    if isinstance(value, (date, datetime)):
        return {"type": "date", "value": value.isoformat()}
    if isinstance(value, bool):
        return {"type": "boolean", "value": value}
    if isinstance(value, (int, float)):
        return {"type": "number", "value": str(value)}
    return {"type": "text", "value": str(value)}


def generate(directory: Path, output: Path) -> dict:
    paths = [directory / name for name in FILES]
    for path in paths:
        if not path.is_file():
            raise ValueError(f"Required workbook missing: {path.name}")
    digests = [hashlib.sha256(path.read_bytes()).hexdigest() for path in paths]
    batch = hashlib.sha256("\n".join(digests).encode()).hexdigest()
    counts = {}
    with output.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("CREATE DATABASE IF NOT EXISTS oms_registry_staging CHARACTER SET utf8mb4;\nUSE oms_registry_staging;\n")
        stream.write("CREATE TABLE IF NOT EXISTS source_workbooks (batch_id CHAR(64) NOT NULL, file_name VARCHAR(255) NOT NULL, sha256 CHAR(64) NOT NULL, PRIMARY KEY(batch_id,file_name));\n")
        stream.write("CREATE TABLE IF NOT EXISTS source_rows (batch_id CHAR(64) NOT NULL, file_name VARCHAR(255) NOT NULL, sheet_name VARCHAR(255) NOT NULL, source_row_number INT NOT NULL, cells JSON NOT NULL, PRIMARY KEY(batch_id,file_name,sheet_name,source_row_number));\nSTART TRANSACTION;\n")
        for path, digest in zip(paths, digests):
            values = load_workbook(path, data_only=True, read_only=False)
            formulas = load_workbook(path, data_only=False, read_only=False)
            count = 0
            stream.write(f"INSERT INTO source_workbooks VALUES ({sql_text(batch)},{sql_text(path.name)},{sql_text(digest)}) ON DUPLICATE KEY UPDATE batch_id=batch_id;\n")
            for sheet in formulas:
                cached = values[sheet.title]
                for row in sheet:
                    cells = {}
                    for cell in row:
                        if cell.value is None and not cell.hyperlink:
                            continue
                        source = cached.cell(cell.row, cell.column)
                        item = typed(source.value) if source.value is not None else {"type": "missing", "value": None}
                        if source.data_type == "e":
                            item["type"] = "error"
                        if cell.data_type == "f":
                            formula = cell.value
                            item["formula"] = formula if isinstance(formula, str) else {
                                "text": getattr(formula, "text", None),
                                "ref": getattr(formula, "ref", None),
                                "kind": type(formula).__name__,
                            }
                        if cell.hyperlink:
                            item["hyperlink"] = cell.hyperlink.target or cell.hyperlink.location
                        cells[get_column_letter(cell.column)] = item
                    if cells:
                        payload = json.dumps(cells, ensure_ascii=False, separators=(",", ":"))
                        stream.write(f"INSERT INTO source_rows VALUES ({sql_text(batch)},{sql_text(path.name)},{sql_text(sheet.title)},{row[0].row},{sql_text(payload)}) ON DUPLICATE KEY UPDATE batch_id=batch_id;\n")
                        count += 1
            values.close()
            formulas.close()
            counts[path.name] = count
        stream.write("COMMIT;\n")
        stream.write(f"SELECT file_name,COUNT(*) AS source_rows FROM source_rows WHERE batch_id={sql_text(batch)} GROUP BY file_name;\n")
    return {"batch": batch, "rows": counts}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    print(json.dumps(generate(args.directory, args.output), ensure_ascii=True))
