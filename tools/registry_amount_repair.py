"""Build a guarded monetary-attribute repair from a read-only JSON-lines registry export."""
import json
import re
import sys
from decimal import Decimal, InvalidOperation


def decimal_amount(text):
    compact = re.sub(r"\s|\u00a0", "", text or "")
    if "," in compact and "." in compact:
        compact = compact.replace(",", "")
    elif re.fullmatch(r"-?\d{1,3}(,\d{3})+", compact):
        compact = compact.replace(",", "")
    else:
        compact = compact.replace(",", ".")
    try:
        value = Decimal(compact)
        return value if value.is_finite() and value >= 0 else None
    except InvalidOperation:
        return None


def amount(text):
    value = decimal_amount(text)
    return int(value) if value is not None else None


def broken_amount(text):
    try:
        value = Decimal(re.sub(r"\s", "", text or "").replace(",", "."))
        return int(value) if value.is_finite() else 0
    except InvalidOperation:
        return 0


def sql_text(text):
    return "NULL" if text is None else "CONVERT(X'" + text.encode("utf-8").hex() + "' USING utf8mb4)"


def plan(rows, currency_only=False):
    for row in rows:
        changes = {}
        new_budget = amount(row["source_budget"])
        new_contract = amount(row["source_contract"])
        if currency_only:
            if row.get("currency") == "EUR" and new_budget is not None and row["budget"] == new_budget:
                yield row, {"currency": "UAH"}
            continue
        if new_budget is not None and row["budget"] == max(0, broken_amount(row["source_budget"])):
            if new_budget != row["budget"]:
                changes["budget_planned"] = new_budget
        old_contract = broken_amount(row["source_contract"])
        old_contract = old_contract if old_contract > 0 else None
        if new_contract and row["contract"] == old_contract and new_contract != row["contract"]:
            changes["subproject_contract_amount"] = new_contract
        if changes:
            yield row, changes


def main():
    rows = [json.loads(line) for line in sys.stdin if line.strip()]
    currency_only = sys.argv[1:] == ["--currency-only"]
    if sys.argv[1:] and not currency_only:
        raise SystemExit("Usage: registry_amount_repair.py [--currency-only]")
    updates = list(plan(rows, currency_only))
    # A duplicate-key assertion aborts even on MySQL versions without enforced CHECK constraints.
    print("CREATE TEMPORARY TABLE oms_amount_repair_assertion (id INT PRIMARY KEY);")
    print("INSERT INTO oms_amount_repair_assertion VALUES (1);")
    print("START TRANSACTION;")
    print("SET @repaired_rows=0;")
    for row, changes in updates:
        expected = "NULL" if row["contract"] is None else str(int(row["contract"]))
        assignments = ",".join("p." + key + "=" + (sql_text(value) if isinstance(value, str) else str(value))
                               for key, value in changes.items())
        currency_guard = " AND p.currency=" + sql_text(row["currency"]) if "currency" in row else ""
        print("UPDATE projects p JOIN project_monitoring_details d ON d.project_id=p.id SET " + assignments
              + " WHERE p.id=" + str(int(row["id"]))
              + " AND p.site_number=" + sql_text(row["code"])
              + " AND p.budget_planned=" + str(int(row["budget"]))
              + " AND p.subproject_contract_amount <=> " + expected
              + currency_guard
              + " AND JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.fundingTotal')) <=> " + sql_text(row["source_budget"])
              + " AND JSON_UNQUOTE(JSON_EXTRACT(d.registry_json,'$.contractedTotal')) <=> " + sql_text(row["source_contract"])
              + ";")
        print("SET @repaired_rows=@repaired_rows+ROW_COUNT();")
    print("INSERT INTO oms_amount_repair_assertion SELECT 1 WHERE @repaired_rows<>" + str(len(updates)) + ";")
    print("SELECT @repaired_rows AS repaired_rows;")
    print("COMMIT;")
    print(json.dumps({"source_rows": len(rows), "repair_rows": len(updates),
                      "budget_fields": sum("budget_planned" in change for _, change in updates),
                      "contract_fields": sum("subproject_contract_amount" in change for _, change in updates),
                      "currency_fields": sum("currency" in change for _, change in updates)}), file=sys.stderr)


if __name__ == "__main__":
    main()
