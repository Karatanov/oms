"""Read-only classification of project budget representations; never updates source/API/DB values."""
import json
import re
import urllib.request
import subprocess
from pathlib import Path
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation


def source_money(raw):
    compact = re.sub(r'\s', '', str(raw) if raw is not None else '')
    if ',' in compact and '.' in compact or re.fullmatch(r'-?\d{1,3}(,\d{3})+', compact):
        compact = compact.replace(',', '')
    else:
        compact = compact.replace(',', '.')
    try:
        value = Decimal(compact)
        return value if value.is_finite() and value >= 0 else None
    except InvalidOperation:
        return None


def classify(project):
    source = source_money(project.get('registry', {}).get('fundingTotal'))
    snapshot = project.get('amounts', {}).get('budget')
    if source is None:
        return 'no_source_budget', None
    if not snapshot:
        return 'no_saved_snapshot', None
    saved = source_money(snapshot['amount'] if snapshot['currency'] == 'UAH' else snapshot['convertedAmount'])
    if saved is None:
        return 'invalid_saved_snapshot', None
    legacy = Decimal(str(project['budgetPlanned']))
    # The legacy field intentionally stores whole hryvnias; snapshot and source retain cents.
    source_legacy_matches = abs(source - legacy) < Decimal('1.01')
    source_snapshot_matches = abs(source - saved) <= Decimal('0.01')
    category = ('consistent_at_legacy_precision' if source_legacy_matches and source_snapshot_matches else
                'legacy_differs_only' if source_snapshot_matches else
                'snapshot_differs_only' if source_legacy_matches else 'both_differ_from_source')
    return category, dict(code=project['siteNumber'], sourceUah=str(source), legacyUah=str(legacy),
                          snapshotUah=str(saved), snapshotCurrency=snapshot['currency'],
                          conversionEdited=snapshot.get('conversionEdited', False))


def main():
    def request(path, token=None):
        headers = {'Cache-Control': 'no-cache'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('https://ua-oms.com' + path, headers=headers,
                                     data=b'' if path.endswith('/auth/guest') else None)
        with urllib.request.urlopen(req, timeout=45) as response:
            return json.load(response, parse_float=Decimal)
    token = request('/api/v1/auth/guest')['accessToken']
    projects = request('/api/v1/projects?pageSize=500', token)['data']
    root = Path(__file__).resolve().parents[1]
    exported = subprocess.run(['ssh', '-i', str(Path.home() / '.ssh/oms_vps_codex'), '-o', 'BatchMode=yes',
                               'root@2.24.162.154', "tr -d '\\r' | bash"],
                              input=(root / 'deploy/vps/export-calculation-audit.sh').read_bytes(),
                              capture_output=True, check=True)
    stored = {row['uuid']: json.loads(row['registry'] or '{}') for line in exported.stdout.decode().splitlines()
              if (row := json.loads(line, parse_float=Decimal))['kind'] == 'project'}
    counts, examples, edited = Counter(), defaultdict(list), Counter()
    for project in projects:
        if project.get('projectType') not in ('subproject', 'subproject_part'):
            continue
        # API registry fallbacks are derived from these same amounts, not independent source evidence.
        category, evidence = classify(dict(project, registry=stored.get(project['uuid'], {})))
        counts[category] += 1
        if evidence and not category.startswith('consistent'):
            edited[str(evidence['conversionEdited'])] += 1
            if len(examples[category]) < 8:
                examples[category].append(evidence)
    print(json.dumps(dict(revision=request('/version'), classification=dict(counts),
                         conflictingConversionEdits=dict(edited), examples=dict(examples),
                         sourceEvidence='raw stored monitoring registry, not API fallbacks',
                         databaseWrites=False), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
