"""Read-only denominator provenance audit; never imports or updates a workbook/DB."""
import json
import subprocess
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

from audit_budget_representations import source_money


def execution_bases(rows):
    projects = [row for row in rows if row['kind'] == 'project']
    procurement = [row for row in rows if row['kind'] == 'procurement']
    children = defaultdict(list)
    for row in projects:
        if row['type'] == 'subproject_part':
            children[row['parent']].append(row)

    def works(row):
        value = source_money(json.loads(row.get('registry') or '{}').get('constructionAmount'))
        return value if value is not None and value > 0 else None

    result = []
    for row in projects:
        if row['type'] != 'subproject':
            continue
        parent = works(row)
        parts = [value for part in children[row['id']] if (value := works(part)) is not None]
        source_lots = sum(parts, Decimal(0))
        candidates = [('parent_source', parent), ('lot_sources', source_lots),
                      ('stored_contract', row['contract']), ('approved_budget', row['budget'])]
        provenance, base = next(((kind, Decimal(str(value))) for kind, value in candidates
                                 if value is not None and value > 0), ('unavailable', Decimal(0)))
        binary_lots = sum(float(value) for value in parts)
        missing = [part for part in children[row['id']] if works(part) is None]
        result.append(dict(code=row['code'], status=row['status'], provenance=provenance,
                           baseUah=str(base), knownLotAmounts=len(parts),
                           totalParts=len(children[row['id']]),
                           lotBinaryDriftUah=str(Decimal(str(binary_lots)) - source_lots)
                           if provenance == 'lot_sources' else '0',
                           missingLotSourceEvidence=[dict(code=part['code'], procurements=[
                               dict(type=record.get('type'), status=record.get('status'),
                                    contractUah=str(record.get('amount')), signedUah=str(record.get('signed')))
                               for record in procurement
                               if (record.get('lot') or record.get('code') or '').strip().upper()
                               == part['code'].strip().upper()]) for part in missing]
                           if provenance == 'lot_sources' else []))
    return result


def main():
    root = Path(__file__).resolve().parents[1]
    export = subprocess.run(['ssh', '-i', str(Path.home() / '.ssh/oms_vps_codex'), '-o', 'BatchMode=yes',
                             'root@2.24.162.154', "tr -d '\\r' | bash"],
                            input=(root / 'deploy/vps/export-calculation-audit.sh').read_bytes(),
                            capture_output=True, check=True)
    rows = [json.loads(line, parse_float=Decimal) for line in export.stdout.decode('utf-8').splitlines()]
    bases = execution_bases(rows)
    print(json.dumps(dict(databaseWrites=False, subprojects=len(bases),
                         provenance=dict(Counter(row['provenance'] for row in bases)),
                         lotBased=[row for row in bases if row['provenance'] == 'lot_sources'],
                         unavailable=[row['code'] for row in bases if row['provenance'] == 'unavailable']),
                     ensure_ascii=True, indent=2))


if __name__ == '__main__':
    main()
