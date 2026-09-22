"""Create an idempotent procurement backfill for the authoritative Tranche B plan.

The workbook is an import source only.  This script writes an auditable JSON
snapshot and a Flyway migration; the running application never accesses a
user-local workbook path.
"""

from __future__ import annotations

import argparse
import json
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path

from openpyxl import load_workbook


ALLOWED_TYPES = {"W", "TS", "CSC"}


def clean(value):
    if value is None:
        return None
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, str):
        value = " ".join(value.replace("\r", " ").replace("\n", " ").split())
        return value or None
    if isinstance(value, float):
        return Decimal(str(value))
    return value


def clean_date(value):
    """Keep only complete ISO dates; workbook display values are not dates."""
    value = clean(value)
    if isinstance(value, str) and len(value) == 10:
        try:
            return date.fromisoformat(value).isoformat()
        except ValueError:
            pass
    return None


def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def json_value(value):
    return format(value, "f") if isinstance(value, Decimal) else value


def read_records(path: Path) -> list[dict]:
    workbook = load_workbook(path, read_only=True, data_only=True)
    sheet = workbook["4. Procurement Plan (B)"]
    records: list[dict] = []
    seen: set[tuple[str, str]] = set()
    for values in sheet.iter_rows(min_row=10, values_only=True):
        values = [clean(value) for value in values]
        code, type_code = values[8], values[11]
        if not code or type_code not in ALLOWED_TYPES:
            continue
        identity = (str(code), str(type_code))
        if identity in seen:
            raise ValueError(f"Duplicate procurement source row for {identity}")
        seen.add(identity)
        records.append({
            "subprojectCode": code,
            "sourceContractType": values[10],
            "typeCode": type_code,
            "sourceProcurementId": values[12],
            "oblastCode": values[4],
            "promotorName": values[5],
            "subprojectNameUk": values[6],
            "subprojectNameEn": values[7],
            "subprojectTotalCostUah": values[13],
            "subprojectEibFinancingUah": values[14],
            "subprojectLocalFinancingUah": values[15],
            "estimatedTotalEur": values[16],
            "estimatedTotalUah": values[17],
            "estimatedEibEur": values[18],
            "estimatedEibUah": values[19],
            "estimatedLocalEur": values[20],
            "estimatedLocalUah": values[21],
            "procurementMethod": values[22],
            "tenderDocumentType": values[23],
            "publishedInOjeu": values[24],
            "estimatedProzorroDate": clean_date(values[25]),
            "estimatedBidSubmissionDate": clean_date(values[26]),
            "estimatedContractDate": clean_date(values[27]),
            "estimatedContractEndDate": clean_date(values[28]),
            "purchaseStatus": values[29],
            "localFinancingPct": values[31],
            "comments": values[32],
            "sourceStatusCode": values[33],
        })
    return records


COLUMNS = [
    "subproject_code", "source_contract_type", "type_code", "source_procurement_id", "oblast_id",
    "promotor_name", "subproject_name_uk", "subproject_name_en", "subproject_total_cost_uah",
    "subproject_eib_financing_uah", "subproject_local_financing_uah", "estimated_total_eur",
    "estimated_total_uah", "estimated_eib_eur", "estimated_eib_uah", "estimated_local_eur",
    "estimated_local_uah", "procurement_method", "tender_document_type", "published_in_ojeu",
    "estimated_prozorro_date", "estimated_bid_submission_date", "estimated_contract_date",
    "estimated_contract_end_date", "purchase_status", "local_financing_pct", "comments", "source_status_code",
]


def values(record: dict) -> list:
    keys = [
        "subprojectCode", "sourceContractType", "typeCode", "sourceProcurementId", "oblastCode", "promotorName",
        "subprojectNameUk", "subprojectNameEn", "subprojectTotalCostUah", "subprojectEibFinancingUah",
        "subprojectLocalFinancingUah", "estimatedTotalEur", "estimatedTotalUah", "estimatedEibEur",
        "estimatedEibUah", "estimatedLocalEur", "estimatedLocalUah", "procurementMethod", "tenderDocumentType",
        "publishedInOjeu", "estimatedProzorroDate", "estimatedBidSubmissionDate", "estimatedContractDate",
        "estimatedContractEndDate", "purchaseStatus", "localFinancingPct", "comments", "sourceStatusCode",
    ]
    return [record[key] for key in keys]


def build_sql(records: list[dict]) -> str:
    stage_types = """subproject_code VARCHAR(64) NOT NULL, source_contract_type VARCHAR(255) NOT NULL,
    type_code VARCHAR(8) NOT NULL, source_procurement_id VARCHAR(128) NULL, oblast_id VARCHAR(32) NULL,
    promotor_name VARCHAR(500) NULL, subproject_name_uk TEXT NULL, subproject_name_en TEXT NULL,
    subproject_total_cost_uah DECIMAL(18,2) NULL, subproject_eib_financing_uah DECIMAL(18,2) NULL,
    subproject_local_financing_uah DECIMAL(18,2) NULL, estimated_total_eur DECIMAL(18,4) NULL,
    estimated_total_uah DECIMAL(18,2) NULL, estimated_eib_eur DECIMAL(18,4) NULL,
    estimated_eib_uah DECIMAL(18,2) NULL, estimated_local_eur DECIMAL(18,4) NULL,
    estimated_local_uah DECIMAL(18,2) NULL, procurement_method VARCHAR(255) NULL,
    tender_document_type VARCHAR(255) NULL, published_in_ojeu VARCHAR(64) NULL,
    estimated_prozorro_date VARCHAR(32) NULL, estimated_bid_submission_date VARCHAR(32) NULL, estimated_contract_date VARCHAR(32) NULL,
    estimated_contract_end_date VARCHAR(32) NULL, purchase_status VARCHAR(255) NULL, local_financing_pct DECIMAL(14,10) NULL,
    comments TEXT NULL, source_status_code VARCHAR(64) NULL"""
    row_sql = ",\n".join("(" + ", ".join(sql(value) for value in values(record)) + ")" for record in records)
    return f"""-- Generated by tools/generate_tranche_b_procurement_backfill.py from Tranche A + B selected (2).xlsx.
-- The 53 canonical Tranche B procurement rows cover 26 subprojects: 26 works,
-- 26 technical-supervision and 1 engineer-consultant procurement.
-- Existing non-empty OMS values are preserved.  Only absent data and missing
-- relations to the new Tranche B subprojects are restored.

CREATE TEMPORARY TABLE procurement_tranche_b_source (
    {stage_types},
    PRIMARY KEY (subproject_code, type_code)
);

INSERT INTO procurement_tranche_b_source ({", ".join(COLUMNS)}) VALUES
{row_sql};

-- Reattach records that were imported before the matching screening project
-- existed, and fill legacy NULL/empty cells without replacing user edits.
UPDATE procurement_records record
INNER JOIN procurement_tranche_b_source source
    ON source.subproject_code = record.sub_project_id
   AND source.source_contract_type = record.source_contract_type
INNER JOIN projects subproject
    ON subproject.project_type = 'subproject'
   AND subproject.site_number = source.subproject_code
SET record.project_id = COALESCE(record.project_id, subproject.id),
    record.batch_id = 9,
    record.oblast_name = COALESCE(NULLIF(TRIM(record.oblast_name), ''), subproject.region),
    record.oblast_id = COALESCE(NULLIF(TRIM(record.oblast_id), ''), source.oblast_id),
    record.promotor_name = COALESCE(NULLIF(TRIM(record.promotor_name), ''), source.promotor_name),
    record.subproject_name_uk = COALESCE(NULLIF(TRIM(record.subproject_name_uk), ''), source.subproject_name_uk),
    record.subproject_name_en = COALESCE(NULLIF(TRIM(record.subproject_name_en), ''), source.subproject_name_en),
    record.subproject_total_cost_uah = COALESCE(record.subproject_total_cost_uah, source.subproject_total_cost_uah),
    record.subproject_eib_financing_uah = COALESCE(record.subproject_eib_financing_uah, source.subproject_eib_financing_uah),
    record.subproject_local_financing_uah = COALESCE(record.subproject_local_financing_uah, source.subproject_local_financing_uah),
    record.estimated_total_eur = COALESCE(record.estimated_total_eur, source.estimated_total_eur),
    record.estimated_total_uah = COALESCE(record.estimated_total_uah, source.estimated_total_uah),
    record.estimated_eib_eur = COALESCE(record.estimated_eib_eur, source.estimated_eib_eur),
    record.estimated_eib_uah = COALESCE(record.estimated_eib_uah, source.estimated_eib_uah),
    record.estimated_local_eur = COALESCE(record.estimated_local_eur, source.estimated_local_eur),
    record.estimated_local_uah = COALESCE(record.estimated_local_uah, source.estimated_local_uah),
    record.procurement_method = COALESCE(NULLIF(TRIM(record.procurement_method), ''), source.procurement_method),
    record.tender_document_type = COALESCE(NULLIF(TRIM(record.tender_document_type), ''), source.tender_document_type),
    record.published_in_ojeu = COALESCE(NULLIF(TRIM(record.published_in_ojeu), ''), source.published_in_ojeu),
    record.estimated_prozorro_date = COALESCE(record.estimated_prozorro_date, CASE WHEN source.estimated_prozorro_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_prozorro_date) END),
    record.estimated_bid_submission_date = COALESCE(record.estimated_bid_submission_date, CASE WHEN source.estimated_bid_submission_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_bid_submission_date) END),
    record.estimated_contract_date = COALESCE(record.estimated_contract_date, CASE WHEN source.estimated_contract_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_contract_date) END),
    record.estimated_contract_end_date = COALESCE(record.estimated_contract_end_date, CASE WHEN source.estimated_contract_end_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_contract_end_date) END),
    record.purchase_status = COALESCE(NULLIF(TRIM(record.purchase_status), ''), source.purchase_status),
    record.local_financing_pct = COALESCE(record.local_financing_pct, source.local_financing_pct),
    record.comments = COALESCE(NULLIF(TRIM(record.comments), ''), source.comments),
    record.source_status_code = COALESCE(NULLIF(TRIM(record.source_status_code), ''), source.source_status_code);

-- Insert only a truly absent source row.  `source_contract_type` is the OMS
-- business key for W / TS / CSC after obsolete source identifiers were removed.
INSERT INTO procurement_records (
    batch_id, oblast_name, oblast_id, promotor_name, subproject_name_uk, subproject_name_en,
    sub_project_id, source_contract_type, subproject_total_cost_uah, subproject_eib_financing_uah,
    subproject_local_financing_uah, estimated_total_eur, estimated_total_uah, estimated_eib_eur,
    estimated_eib_uah, estimated_local_eur, estimated_local_uah, procurement_method,
    tender_document_type, published_in_ojeu, estimated_prozorro_date, estimated_bid_submission_date,
    estimated_contract_date, estimated_contract_end_date, purchase_status, local_financing_pct,
    comments, source_status_code, project_id
)
SELECT 9, subproject.region, source.oblast_id, source.promotor_name, source.subproject_name_uk,
    source.subproject_name_en, source.subproject_code, source.source_contract_type,
    source.subproject_total_cost_uah, source.subproject_eib_financing_uah, source.subproject_local_financing_uah,
    source.estimated_total_eur, source.estimated_total_uah, source.estimated_eib_eur,
    source.estimated_eib_uah, source.estimated_local_eur, source.estimated_local_uah,
    source.procurement_method, source.tender_document_type, source.published_in_ojeu,
    CASE WHEN source.estimated_prozorro_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_prozorro_date) END,
    CASE WHEN source.estimated_bid_submission_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_bid_submission_date) END,
    CASE WHEN source.estimated_contract_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_contract_date) END,
    CASE WHEN source.estimated_contract_end_date REGEXP '^[0-9]{{4}}-[0-9]{{2}}-[0-9]{{2}}$' THEN DATE(source.estimated_contract_end_date) END,
    source.purchase_status, source.local_financing_pct,
    source.comments, source.source_status_code, subproject.id
FROM procurement_tranche_b_source source
INNER JOIN projects subproject
    ON subproject.project_type = 'subproject'
   AND subproject.site_number = source.subproject_code
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_records record
    WHERE record.sub_project_id = source.subproject_code
      AND record.source_contract_type = source.source_contract_type
);

DROP TEMPORARY TABLE procurement_tranche_b_source;
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--sql-output", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    args = parser.parse_args()
    records = read_records(args.source)
    assert len(records) == 53, f"Expected 53 source records, got {{len(records)}}"
    assert sum(record["typeCode"] == "W" for record in records) == 26
    assert sum(record["typeCode"] == "TS" for record in records) == 26
    assert sum(record["typeCode"] == "CSC" for record in records) == 1
    args.sql_output.parent.mkdir(parents=True, exist_ok=True)
    args.json_output.parent.mkdir(parents=True, exist_ok=True)
    args.sql_output.write_text(build_sql(records), encoding="utf-8")
    args.json_output.write_text(json.dumps({
        "sourceWorkbook": args.source.name,
        "tranche": "B",
        "recordCount": len(records),
        "subprojectCount": len({record["subprojectCode"] for record in records}),
        "records": records,
    }, ensure_ascii=False, indent=2, default=json_value), encoding="utf-8")


if __name__ == "__main__":
    main()
