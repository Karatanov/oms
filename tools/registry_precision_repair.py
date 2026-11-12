"""Restore only source money fields provably equal to the former rounded display value."""
import json
import sys
from registry_amount_repair import decimal_amount, sql_text

MONEY_KEYS = {"fundingTotal", "fundingWorks", "fundingTs", "fundingCsc", "eibCost", "localCost",
              "contractedTotal", "contractedEib", "contractedLocal", "constructionAmount",
              "constructionEib", "constructionLocal", "paidConstruction"}


def plan(rows, sources):
    for row in rows:
        source = sources.get((row["workbook"], row["code"].strip().upper()))
        if source is None:
            continue
        registry = json.loads(row["registry"])
        fields = {}
        projects = {}
        for key, value in source["fields"].items():
            if key not in MONEY_KEYS:
                continue
            previous = decimal_amount(registry.get(key))
            old_display = decimal_amount(value["old"])
            exact = decimal_amount(value["new"])
            if previous is None or exact is None or previous == exact or previous != old_display:
                continue
            fields[key] = value["new"]
            target = {"fundingTotal": ("budget", "budget_planned"),
                      "contractedTotal": ("contract", "subproject_contract_amount")}.get(key)
            if target and row[target[0]] == int(previous) and int(exact) != row[target[0]]:
                projects[target[1]] = int(exact)
        if fields:
            yield row, fields, projects


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: registry_precision_repair.py source-money.jsonl")
    with open(sys.argv[1], encoding="utf-8-sig") as source_file:
        sources = {}
        for line in source_file:
            source = json.loads(line)
            key = source["workbook"], source["code"]
            if key in sources:
                raise ValueError("Ambiguous source code: " + source["code"])
            sources[key] = source
    rows = [json.loads(line) for line in sys.stdin if line.strip()]
    repairs = list(plan(rows, sources))
    print("CREATE TEMPORARY TABLE oms_precision_assertion (id INT PRIMARY KEY);")
    print("INSERT INTO oms_precision_assertion VALUES (1);")
    print("START TRANSACTION; SET @details=0; SET @projects=0;")
    for row, fields, projects in repairs:
        changes = ",".join(sql_text("$." + key) + "," + sql_text(value) for key, value in fields.items())
        expected_contract = "NULL" if row["contract"] is None else str(row["contract"])
        guard = ("p.id=" + str(int(row["id"])) + " AND p.budget_planned=" + str(int(row["budget"]))
                 + " AND p.subproject_contract_amount <=> " + expected_contract)
        print("UPDATE project_monitoring_details d JOIN projects p ON p.id=d.project_id"
              + " SET d.registry_json=JSON_SET(d.registry_json," + changes + ") WHERE " + guard
              + " AND d.source_lot_id=" + sql_text(row["code"])
              + " AND d.source_workbook=" + sql_text(row["workbook"])
              + " AND BINARY d.registry_json=BINARY " + sql_text(row["registry"]) + ";")
        print("SET @details=@details+ROW_COUNT();")
        if projects:
            print("UPDATE projects p SET " + ",".join(key + "=" + str(value) for key, value in projects.items())
                  + " WHERE " + guard + ";")
            print("SET @projects=@projects+ROW_COUNT();")
    project_rows = sum(bool(projects) for _, _, projects in repairs)
    print("INSERT INTO oms_precision_assertion SELECT 1 WHERE @details<>" + str(len(repairs))
          + " OR @projects<>" + str(project_rows) + ";")
    print("SELECT @details AS precise_registry_rows,@projects AS aligned_legacy_rows; COMMIT;")
    print(json.dumps({"exported_rows": len(rows), "repair_rows": len(repairs),
                      "money_fields": sum(len(fields) for _, fields, _ in repairs),
                      "legacy_project_rows": project_rows}), file=sys.stderr)


if __name__ == "__main__":
    main()
